package com.ctps.ctps_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:ctps-app-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=NUMBER",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class CtpsApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
