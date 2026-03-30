import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { chromium } from 'playwright';

const DEFAULT_URL_TEMPLATE = 'https://school.programmers.co.kr/learn/challenges?page={page}';
const DEFAULT_RETENTION = 10;
const DEFAULT_DELAY_MS = 250;
const DEFAULT_PAGES = 3;
const DEFAULT_MODE = 'incremental';

function parseArgs(argv) {
  const options = {
    importDir: process.env.PROGRAMMERS_IMPORT_DIR ?? '',
    pages: Number.parseInt(process.env.PROGRAMMERS_CRAWLER_PAGES ?? String(DEFAULT_PAGES), 10),
    urlTemplate: process.env.PROGRAMMERS_CRAWLER_URL_TEMPLATE ?? DEFAULT_URL_TEMPLATE,
    delayMs: Number.parseInt(process.env.PROGRAMMERS_CRAWLER_DELAY_MS ?? String(DEFAULT_DELAY_MS), 10),
    retention: Number.parseInt(process.env.PROGRAMMERS_CRAWLER_RETENTION ?? String(DEFAULT_RETENTION), 10),
    ingestUrl: process.env.CTPS_PROGRAMMERS_INGEST_URL ?? '',
    adminToken: process.env.ADMIN_SECURITY_TOKEN ?? '',
    mode: process.env.PROGRAMMERS_CRAWLER_MODE ?? DEFAULT_MODE,
    dryRun: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const next = argv[index + 1];
    switch (arg) {
      case '--import-dir':
        options.importDir = next ?? '';
        index += 1;
        break;
      case '--pages':
        options.pages = Number.parseInt(next ?? String(DEFAULT_PAGES), 10);
        index += 1;
        break;
      case '--url-template':
        options.urlTemplate = next ?? DEFAULT_URL_TEMPLATE;
        index += 1;
        break;
      case '--delay-ms':
        options.delayMs = Number.parseInt(next ?? String(DEFAULT_DELAY_MS), 10);
        index += 1;
        break;
      case '--retention':
        options.retention = Number.parseInt(next ?? String(DEFAULT_RETENTION), 10);
        index += 1;
        break;
      case '--ingest-url':
        options.ingestUrl = next ?? '';
        index += 1;
        break;
      case '--admin-token':
        options.adminToken = next ?? '';
        index += 1;
        break;
      case '--mode':
        options.mode = next ?? DEFAULT_MODE;
        index += 1;
        break;
      case '--full-refresh':
        options.mode = 'full-refresh';
        break;
      case '--dry-run':
        options.dryRun = true;
        break;
      default:
        break;
    }
  }

  if (!Number.isFinite(options.pages) || options.pages <= 0) {
    options.pages = DEFAULT_PAGES;
  }
  if (!Number.isFinite(options.delayMs) || options.delayMs < 0) {
    options.delayMs = DEFAULT_DELAY_MS;
  }
  if (!Number.isFinite(options.retention) || options.retention <= 0) {
    options.retention = DEFAULT_RETENTION;
  }
  if (!['incremental', 'full-refresh'].includes(options.mode)) {
    options.mode = DEFAULT_MODE;
  }

  return options;
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function normalizeWhitespace(value) {
  return String(value ?? '')
    .replace(/\s+/g, ' ')
    .trim();
}

function normalizeDifficulty(rawValue) {
  const text = normalizeWhitespace(rawValue).toLowerCase();
  if (!text) return 'medium';
  const levelMatch = text.match(/(?:lv|level|레벨|난이도)\s*\.?\s*(\d)/i);
  if (levelMatch) {
    const level = Number.parseInt(levelMatch[1], 10);
    if (level <= 1) return 'easy';
    if (level <= 3) return 'medium';
    return 'hard';
  }
  if (text.includes('lv. 0') || text.includes('level 0') || text.includes('easy')) return 'easy';
  if (text.includes('lv. 1') || text.includes('level 1')) return 'easy';
  if (
    text.includes('lv. 4')
    || text.includes('lv. 5')
    || text.includes('level 4')
    || text.includes('level 5')
    || text.includes('hard')
  ) {
    return 'hard';
  }
  return 'medium';
}

function expandTags(partText) {
  const cleaned = normalizeWhitespace(partText);
  if (!cleaned) return [];

  const tags = new Set(
    cleaned
      .split(/[|,>]/)
      .flatMap((part) => part.split('/'))
      .map((part) => normalizeWhitespace(part))
      .filter(Boolean)
  );
  tags.add(cleaned);
  const aliasMatches = cleaned.match(/\(([^)]+)\)/g) ?? [];
  for (const aliasGroup of aliasMatches) {
    const aliases = aliasGroup
      .replace(/[()]/g, '')
      .split('/')
      .map((value) => normalizeWhitespace(value))
      .filter(Boolean);
    for (const alias of aliases) {
      tags.add(alias);
    }
  }
  return [...tags];
}

function firstPresent(candidate, keys) {
  for (const key of keys) {
    const value = candidate?.[key];
    if (value !== null && value !== undefined && value !== '' && (!Array.isArray(value) || value.length > 0)) {
      return value;
    }
  }
  return null;
}

function* walkCandidates(node) {
  if (Array.isArray(node)) {
    for (const item of node) {
      yield* walkCandidates(item);
    }
    return;
  }
  if (node && typeof node === 'object') {
    yield node;
    for (const value of Object.values(node)) {
      yield* walkCandidates(value);
    }
  }
}

function normalizeEmbeddedTags(rawValue) {
  if (!rawValue) return [];
  if (typeof rawValue === 'string') {
    return expandTags(rawValue);
  }
  if (Array.isArray(rawValue)) {
    return [...new Set(rawValue.flatMap((entry) => {
      if (typeof entry === 'string') return expandTags(entry);
      if (entry && typeof entry === 'object') {
        const value = firstPresent(entry, ['name', 'label', 'title', 'value']);
        return typeof value === 'string' ? expandTags(value) : [];
      }
      return [];
    }))];
  }
  return [];
}

async function extractEmbeddedLessonMetadata(page, lesson) {
  const nextDataText = await page.locator('script#__NEXT_DATA__').textContent().catch(() => '');
  if (!nextDataText) {
    return { title: '', difficulty: '', tags: [] };
  }

  try {
    const payload = JSON.parse(nextDataText);
    const resolvedTags = new Set();
    let resolvedTitle = '';
    let resolvedDifficulty = '';

    for (const candidate of walkCandidates(payload)) {
      const identifier = String(firstPresent(candidate, ['lessonId', 'lesson_id', 'problemId', 'id']) ?? '');
      const candidateTitle = normalizeWhitespace(firstPresent(candidate, ['title', 'name', 'lessonTitle']) ?? '');
      const sameLesson = identifier === lesson.lessonId || (!!candidateTitle && candidateTitle === lesson.title);
      if (!sameLesson) {
        continue;
      }

      if (!resolvedTitle && candidateTitle) {
        resolvedTitle = candidateTitle;
      }
      if (!resolvedDifficulty) {
        const difficulty = firstPresent(candidate, ['difficulty', 'level', 'difficultyLevel']);
        if (difficulty !== null && difficulty !== undefined) {
          resolvedDifficulty = String(difficulty);
        }
      }

      normalizeEmbeddedTags(firstPresent(candidate, ['tags', 'skills', 'categories', 'categoryNames']))
        .forEach((tag) => resolvedTags.add(tag));
    }

    return {
      title: resolvedTitle,
      difficulty: resolvedDifficulty,
      tags: [...resolvedTags],
    };
  } catch {
    return { title: '', difficulty: '', tags: [] };
  }
}

function buildExternalId(lessonId) {
  return `programmers-${lessonId}`;
}

function resolveAbsoluteUrl(href) {
  return new URL(href, 'https://school.programmers.co.kr').toString();
}

async function collectLessonLinks(page, urlTemplate, pages, delayMs) {
  const lessons = new Map();

  for (let currentPage = 1; currentPage <= pages; currentPage += 1) {
    const url = urlTemplate.replace('{page}', String(currentPage));
    await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });

    const entries = await page.locator('a[href*="/learn/courses/"][href*="/lessons/"]').evaluateAll((anchors) =>
      anchors
        .map((anchor) => ({
          href: anchor.getAttribute('href') ?? '',
          title: (anchor.textContent ?? '').trim(),
        }))
        .filter((entry) => entry.href && entry.title)
    );

    for (const entry of entries) {
      const lessonIdMatch = entry.href.match(/lessons\/(\d+)/);
      if (!lessonIdMatch) {
        continue;
      }

      const lessonId = lessonIdMatch[1];
      if (!lessons.has(lessonId)) {
        lessons.set(lessonId, {
          lessonId,
          title: normalizeWhitespace(entry.title),
          externalUrl: resolveAbsoluteUrl(entry.href),
        });
      }
    }

    if (currentPage < pages && delayMs > 0) {
      await wait(delayMs);
    }
  }

  return [...lessons.values()];
}

async function readLatestSnapshot(importDir) {
  if (!importDir) {
    return [];
  }

  try {
    const files = (await fs.readdir(importDir))
      .filter((file) => file.startsWith('programmers-catalog-') && file.endsWith('.json'))
      .map((file) => path.join(importDir, file));

    if (files.length === 0) {
      return [];
    }

    const statEntries = await Promise.all(
      files.map(async (file) => ({
        file,
        mtimeMs: (await fs.stat(file)).mtimeMs,
      }))
    );

    const latestFile = statEntries.sort((left, right) => right.mtimeMs - left.mtimeMs)[0]?.file;
    if (!latestFile) {
      return [];
    }

    const raw = await fs.readFile(latestFile, 'utf8');
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

async function enrichLesson(page, lesson) {
  await page.goto(lesson.externalUrl, { waitUntil: 'networkidle', timeout: 60000 });
  const embeddedMetadata = await extractEmbeddedLessonMetadata(page, lesson);

  const title =
    normalizeWhitespace(await page.locator('.challenge-title').first().textContent().catch(() => embeddedMetadata.title || lesson.title))
    || embeddedMetadata.title
    || lesson.title;

  const partTexts = await page.locator('a[href*="/learn/courses/30/parts/"]').evaluateAll((anchors) =>
    anchors.map((anchor) => (anchor.textContent ?? '').trim()).filter(Boolean)
  );

  const difficultyTexts = await page
    .locator('text=/Lv\\.|레벨|난이도/i')
    .evaluateAll((nodes) => nodes.map((node) => (node.textContent ?? '').trim()).filter(Boolean))
    .catch(() => []);

  const tags = [...new Set([...embeddedMetadata.tags, ...partTexts.flatMap(expandTags)])];
  const difficulty = normalizeDifficulty(`${embeddedMetadata.difficulty} ${difficultyTexts.join(' ')}`);

  return {
    externalId: buildExternalId(lesson.lessonId),
    title,
    problemNumber: lesson.lessonId,
    difficulty,
    tags,
    externalUrl: lesson.externalUrl,
    recommendationReason: tags.length > 0
      ? `${tags[0]} 유형의 프로그래머스 문제입니다.`
      : '프로그래머스 코딩테스트 연습 문제입니다.',
  };
}

async function collectCatalogItems(options) {
  const existingItems = options.mode === 'incremental'
    ? await readLatestSnapshot(options.importDir)
    : [];
  const existingItemMap = new Map(
    existingItems
      .filter((item) => item && typeof item === 'object' && typeof item.externalId === 'string')
      .map((item) => [item.externalId, item])
  );

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    userAgent:
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 '
      + '(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36',
  });
  const page = await context.newPage();
  const detailPage = await context.newPage();

  try {
    const lessons = await collectLessonLinks(page, options.urlTemplate, options.pages, options.delayMs);
    const newItems = [];

    for (const lesson of lessons) {
      const externalId = buildExternalId(lesson.lessonId);
      if (options.mode === 'incremental' && existingItemMap.has(externalId)) {
        continue;
      }

      const item = await enrichLesson(detailPage, lesson);
      newItems.push(item);
      if (options.delayMs > 0) {
        await wait(options.delayMs);
      }
    }

    const mergedItems = options.mode === 'incremental'
      ? [
          ...existingItems,
          ...newItems,
        ]
      : newItems;

    return {
      items: mergedItems,
      newItemCount: newItems.length,
      existingItemCount: existingItemMap.size,
    };
  } finally {
    await context.close();
    await browser.close();
  }
}

async function writeSnapshot(importDir, items, retention) {
  await fs.mkdir(importDir, { recursive: true });
  const timestamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
  const target = path.join(importDir, `programmers-catalog-${timestamp}.json`);
  const temp = path.join(importDir, `.programmers-catalog-${timestamp}.tmp`);

  await fs.writeFile(temp, `${JSON.stringify(items, null, 2)}\n`, 'utf8');
  await fs.rename(temp, target);

  const files = (await fs.readdir(importDir))
    .filter((file) => file.startsWith('programmers-catalog-') && file.endsWith('.json'))
    .map((file) => path.join(importDir, file));

  const statEntries = await Promise.all(
    files.map(async (file) => ({
      file,
      mtimeMs: (await fs.stat(file)).mtimeMs,
    }))
  );

  for (const { file } of statEntries
    .sort((left, right) => right.mtimeMs - left.mtimeMs)
    .slice(retention)) {
    await fs.rm(file, { force: true });
  }

  return target;
}

async function triggerIngest(ingestUrl, adminToken) {
  if (!ingestUrl) {
    return;
  }

  const response = await fetch(ingestUrl, {
    method: 'POST',
    headers: {
      'User-Agent': 'ctps-programmers-catalog-worker/1.0',
      ...(adminToken ? { 'X-Admin-Token': adminToken } : {}),
    },
  });

  if (!response.ok) {
    throw new Error(`ingest trigger failed with status=${response.status}`);
  }
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (!options.importDir && !options.dryRun) {
    console.error('PROGRAMMERS_IMPORT_DIR 또는 --import-dir 설정이 필요합니다.');
    process.exitCode = 1;
    return;
  }

  const { items, newItemCount, existingItemCount } = await collectCatalogItems(options);
  if (items.length === 0) {
    console.error('수집된 프로그래머스 카탈로그 항목이 없습니다.');
    process.exitCode = 2;
    return;
  }

  if (options.dryRun) {
    console.log(JSON.stringify(items.slice(0, 5), null, 2));
    console.log(
      `dry-run source=playwright mode=${options.mode} total=${items.length} new=${newItemCount} existing=${existingItemCount}`
    );
    return;
  }

  const outputFile = await writeSnapshot(options.importDir, items, options.retention);
  await triggerIngest(options.ingestUrl, options.adminToken);
  console.log(
    `wrote ${items.length} items from playwright to ${outputFile} `
    + `(mode=${options.mode}, new=${newItemCount}, existing=${existingItemCount})`
  );
}

main().catch((error) => {
  console.error(`worker failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 4;
});
