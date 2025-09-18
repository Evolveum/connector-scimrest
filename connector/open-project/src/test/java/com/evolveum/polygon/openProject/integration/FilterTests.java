/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
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


    @Test(enabled = true)
    public void test023SearchAllRoles() {
        testSearchAll("Role");
    }

    @Test(enabled = true)
    public void test024SearchAllMemberships() {
        testSearchAll("Membership");
    }


    ///  Search uid
    @Test(enabled = true)
    public void test031SearchUserByUid() {
        testSearchByUid("User", "2324");
    }
    //TODO
    @Test(enabled = false)
    public void test032SearchGroupByUid() {
        testSearchByUid("Group", "2107");
    }

    @Test(enabled = true)
    public void test033SearchRoleByUid() {
        testSearchByUid("Role", "1");
    }

    @Test(enabled = true)
    public void test034SearchProjectByUid() {
        testSearchByUid("Project", "3");
    }

    @Test(enabled = true)
    public void test035SearchMembershipByUid() {
        testSearchByUid("Membership", "1491:/api/v3/roles/8");
    }

    ///  Search equals filter

    @Test(enabled = true)
    public void test041SearchUserEqualsFilter() {
        testSearchEqualsValue("User", "name","OpenProject Admin");
    }

    @Test(enabled = true)
    public void test042SearchUserContainsFilter() {
        testSearchContainsValue("User", "name","Open");
    }

    @Test(enabled = false)
    public void test044SearchProjectContainsFilter() {
        testSearchContainsValue("Project", "id","3");
    }
}
