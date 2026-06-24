package com.evolveum.polygon.openProject.integration;

import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DeleteTests extends BaseTest{

    private static final Uid UID_GUYBRUSH = new Uid("5");

    @Test(enabled = true)
    public void test10DeleteUser() {

//        testDelete("User", UID_GUYBRUSH);
    }
}
