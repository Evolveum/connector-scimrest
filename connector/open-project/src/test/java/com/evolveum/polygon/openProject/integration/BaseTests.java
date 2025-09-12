package com.evolveum.polygon.openProject.integration;

import com.evolveum.polygon.openProject.OpenProjectConfiguration;
import com.evolveum.polygon.openProject.OpenProjectConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class BaseTests {
    private OpenProjectConnector initializedConnector() {
        var connector = new OpenProjectConnector();
        var configuration = new OpenProjectConfiguration();

        configuration.setBaseAddress("");
        configuration.setUserName("");
        configuration.setPassword(new GuardedString("".toCharArray()));

        connector.init(configuration);
        return connector;
    }

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
    public void test031SearchUserByUid() {
        testSearchByUid("User", "1");
    }

    @Test(enabled = true)
    public void test032SearchGroupByUid() {
        testSearchByUid("Group", "1");
    }

    @Test(enabled = true)
    public void test033SearchRoleByUid() {
        testSearchByUid("Role", "1");
    }

    @Test(enabled = true)
    public void test034SearchProjectByUid() {
        testSearchByUid("Project", "1");
    }

    public void testSchema(String objectType){
        var connector = new OpenProjectConnector();
        var configuration = new OpenProjectConfiguration();
        connector.init(configuration);
        var schema = connector.schema();

        assertNotNull(schema);
        var o = schema.findObjectClassInfo(objectType);
        assertNotNull(o);
        assertNotNull(o.getType());
    }


    public void testSearchAll(String objectType){
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(new ObjectClass(objectType), null,
                results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
    }


    public void testSearchByUid(String objectType, String id) {
        var connector = initializedConnector();
        var results = new ArrayList<ConnectorObject>();
        var filter = new EqualsFilter(new Uid(id));
        connector.executeQuery(new ObjectClass(objectType), filter, results::add, new OperationOptions(Map.of()));
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getUid().getUidValue(), id);
    }
}
