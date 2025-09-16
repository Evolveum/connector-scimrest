package com.evolveum.polygon.openProject.integration;

import org.testng.annotations.Test;
import static org.testng.AssertJUnit.*;


public class StoryTests extends BaseTest {


    @Test(enabled = false)
    public void test110UserProjectMembership() {
      var results = testSearchByUid("User", "1");
        var projects = results.get(0).getAttributeByName("project");
        assertNotNull(projects);
        assertFalse(projects.getValue().isEmpty());
        assertTrue(projects.getValue().stream().
                anyMatch(o -> compareReferencedName(o, "test")));
    }

    @Test(enabled = false)
    public void test120UserGroupMembership() {
        var results = testSearchByUid("User", "1");
        var objects = results.get(0).getAttributeByName("group");
        assertNotNull(objects);
        assertFalse(objects.getValue().isEmpty());
        assertTrue(objects.getValue().stream().
                anyMatch(o -> compareReferencedName(o, "test")));
    }
}
