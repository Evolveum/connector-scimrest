import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.ConnectorObjectReference
import org.identityconnectors.framework.common.objects.ObjectClass

/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
relationship("UserProjectMembership") {
    subject("User") {
        attribute("memberships") {
            multiValued true
            resolver {
                resolutionType PER_OBJECT
                search {
                    attributeFilter("principal").eq(value)
                }
            }
        }
    }
    object("Membership") {
        attribute("principal")
    }
}
relationship("MembershipProject") {

    subject("Membership") {
        attribute("project") {
            //objectClass "Project"
            json {
                path attribute("_links").child("project")
                implementation {
                    deserialize {
                        if(it == null){
                            it = value
                        }
                        var href = it.get("href")?.asText();
                        var pid = href.substring(href.lastIndexOf("/") + 1)

                        var obj = new ConnectorObjectBuilder()
                                .setObjectClass(new ObjectClass("Project"))
                                .setUid(pid)
                                .setName(it.get("title")?.asText())
                        return new ConnectorObjectReference(obj.build());
                    }
                }
            }
        }
    }
    object("Project") {
        attribute("memberships") {
            // FIXME: Add implementation
        }
    }
}

relationship("MembershipRole") {
    subject("Membership") {
        attribute("roles") {

        }
    }
    object("Role") {
        attribute("memberships") {

        }
    }

}