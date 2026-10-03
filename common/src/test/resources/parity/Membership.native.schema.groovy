/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */

import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
import org.identityconnectors.framework.common.objects.ConnectorObjectReference
import org.identityconnectors.framework.common.objects.ObjectClass

objectClass("Membership") {
    embedded(true)
    reference("project") {
        objectClass "Project"
        json {
            type("string")
            openApiFormat("uri-reference")
            path attribute("_links").child("project")
            implementation {
                deserialize {
                    var href = value.get("href")?.asText()
                    var pid = href.substring(href.lastIndexOf("/") + 1)
                    var obj = new ConnectorObjectBuilder()
                            .setObjectClass(new ObjectClass("Project"))
                            .setUid(pid)
                            .setName(value.get("title")?.asText())
                    return new ConnectorObjectReference(obj.build())
                }
            }
        }
    }
}
