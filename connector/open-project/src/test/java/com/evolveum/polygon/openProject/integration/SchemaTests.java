package com.evolveum.polygon.openProject.integration;

import org.testng.annotations.Test;


public class SchemaTests extends BaseTest{


    @Test
    public void test010SchemaUser() {
        testSchema("User");
    }

    @Test
    public void test011SchemaGroup() {
        testSchema("Group");
    }

    @Test
    public void test012SchemaRole() {
        testSchema("Role");
    }

    @Test
    public void test013SchemaProject() {
        testSchema("Project");
    }

}
