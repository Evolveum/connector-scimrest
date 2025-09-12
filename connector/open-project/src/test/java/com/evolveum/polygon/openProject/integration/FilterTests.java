package com.evolveum.polygon.openProject.integration;

import org.testng.annotations.Test;

public class FilterTests extends BaseTest{

    ///  Search all
    @Test(enabled = true)
    public void test020SearchAllUsers() {
        testSearchAll("User");
    }

    @Test(enabled = true)
    public void test021SearchAllGroups() {
        testSearchAll("Group");
    }

    @Test(enabled = true)
    public void test022SearchAllProjects() {
        testSearchAll("Project");
    }

    //TODO
    @Test(enabled = false)
    public void test023SearchAllRoles() {
        testSearchAll("Role");
    }

    ///  Search uid
    @Test(enabled = true)
    public void test031SearchUserByUid() {
        testSearchByUid("User", "1");
    }
    //TODO
    @Test(enabled = false)
    public void test032SearchGroupByUid() {
        testSearchByUid("Group", "");
    }

    @Test(enabled = true)
    public void test033SearchRoleByUid() {
        testSearchByUid("Role", "1");
    }

    @Test(enabled = true)
    public void test034SearchProjectByUid() {
        testSearchByUid("Project", "3");
    }

    ///  Search equals filter

    @Test(enabled = true)
    public void test041SearchUserEqualsFilter() {
        testSearchEqualsValue("User", "name","OpenProject Admin");
    }

    @Test(enabled = true)
    public void test041SearchUserContainsFilter() {
        testSearchContainsValue("User", "name","Open");
    }

    @Test(enabled = false)
    public void test044SearchProjectContainsFilter() {
        testSearchContainsValue("Project", "id","3");
    }
}
