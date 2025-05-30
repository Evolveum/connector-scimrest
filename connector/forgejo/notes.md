### Possible Entities

List of entities identified by AI based only on [list of endpoints & their descriptions](forgejo-api-endpoints.md):

- **User**: Represents an individual account with access to the system.
- **Organization**: A group that owns repositories and manages access policies.
- **Team**: A sub-group within an organization assigned specific repository permissions.
- **Public Key**: Authentication credential (e.g., SSH key) used for access control.
- **Quota Group**: A governance entity defining resource usage limits for users or organizations.
- **Quota Rule**: A policy within a quota group that enforces specific usage constraints.
- **Repository**: An entity with access controls, permissions, and ownership management.
- **Permission Level**: Hierarchical access rights (e.g., read, write, admin) assigned to users or groups.

### Relationships Between Entities

Relationship between selected entities identified by using list of entities and [list of API endpoints](forgejo-api-endpoints.md):

#### **User ↔ Organization**
- **Description**: A user can be a member of an organization.
- **Declaring side**: **Organization** (e.g., via `POST /admin/orgs/{org}/members`).

#### **User ↔ Team**
- **Description**: A user can be a member of a team.
- **Declaring side**: **Team** (e.g., via `POST /teams/{team}/members`).

#### **User ↔ Repository**
- **Description**: A user can have access to a repository (as a collaborator, owner, or via team membership).
- **Declaring side**: **Repository** (e.g., via `GET /repos/{owner}/{repo}/collaborators`).

#### **Organization ↔ Team**
- **Description**: An organization can create and manage teams.
- **Declaring side**: **Organization** (e.g., via `POST /admin/orgs/{org}/teams`).

#### **Organization ↔ Repository**
- **Description**: An organization can own or create a repository.
- **Declaring side**: **Organization** (e.g., via `POST /admin/orgs/{org}/repos`).

#### **Team ↔ Repository**
- **Description**: A team can be granted access to a repository.
- **Declaring side**: **Repository** (e.g., via `POST /repos/{owner}/{repo}/teams`).

#### **User ↔ Repository (via creation)**
- **Description**: A user can create a repository directly.
- **Declaring side**: **User** (e.g., via `POST /user/repos`).