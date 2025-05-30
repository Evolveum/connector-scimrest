# Forgejo API Endpoints

GET /activitypub/actor
Returns the instance's Actor

POST /activitypub/actor/inbox
Send to the inbox

GET
/activitypub/repository-id/{repository-id}
Returns the Repository actor for a repo



POST
/activitypub/repository-id/{repository-id}/inbox
Send to the inbox



GET
/activitypub/user-id/{user-id}
Returns the Person actor for a user



POST
/activitypub/user-id/{user-id}/inbox
Send to the inbox


## Admin


GET admin/cron
List cron tasks

POST admin/cron/{task}
Run cron task

GET admin/emails
List all emails



GET admin/emails/search
Search all emails



GET admin/hooks
List system's webhooks



POST admin/hooks
Create a hook



GET admin/hooks/{id}
Get a hook



DELETE admin/hooks/{id}
Delete a hook



PATCH admin/hooks/{id}
Update a hook



GET admin/orgs
List all organizations



GET admin/quota/groups
List the available quota groups



POST admin/quota/groups
Create a new quota group



GET admin/quota/groups/{quotagroup}
Get information about the quota group



DELETE admin/quota/groups/{quotagroup}
Delete a quota group



PUT admin/quota/groups/{quotagroup}/rules/{quotarule}
Adds a rule to a quota group



DELETE admin/quota/groups/{quotagroup}/rules/{quotarule}
Removes a rule from a quota group



GET admin/quota/groups/{quotagroup}/users
List users in a quota group



PUT admin/quota/groups/{quotagroup}/users/{username}
Add a user to a quota group



DELETE admin/quota/groups/{quotagroup}/users/{username}
Remove a user from a quota group



GET admin/quota/rules
List the available quota rules



POST admin/quota/rules
Create a new quota rule



GET admin/quota/rules/{quotarule}
Get information about a quota rule



DELETE admin/quota/rules/{quotarule}
Deletes a quota rule



PATCH admin/quota/rules/{quotarule}
Change an existing quota rule



GET admin/runners/jobs
Search action jobs according filter conditions



GET admin/runners/registration-token
Get an global actions runner registration token



GET admin/unadopted
List unadopted repositories



POST admin/unadopted/{owner}/{repo}
Adopt unadopted files as a repository



DELETE admin/unadopted/{owner}/{repo}
Delete unadopted files



GET admin/users
Search users according filter conditions



POST admin/users
Create a user



DELETE admin/users/{username}
Delete a user



PATCH admin/users/{username}
Edit an existing user



POST admin/users/{username}/keys
Add a public key on behalf of a user



DELETE admin/users/{username}/keys/{id}
Delete a user's public key



POST admin/users/{username}/orgs
Create an organization



GET admin/users/{username}/quota
Get the user's quota info



POST admin/users/{username}/quota/groups
Set the user's quota groups to a given list.



POST admin/users/{username}/rename
Rename a user



POST admin/users/{username}/repos
Create a repository on behalf of a user


miscellaneous




GET gitignore/templates
Returns a list of all gitignore templates



GET gitignore/templates/{name}
Returns information about a gitignore template



GET label/templates
Returns a list of all label templates



GET label/templates/{name}
Returns all labels in a template



GET licenses
Returns a list of all license templates



GET licenses/{name}
Returns information about a license template



POST markdown
Render a markdown document as HTML



POST markdown/raw
Render raw markdown as HTML



POST markup
Render a markup document as HTML



GET nodeinfo
Returns the nodeinfo of the Forgejo application



GET signing-key.gpg
Get default signing-key.gpg



GET version
Returns the version of the running application

notification

notification


GET notifications
List users's notification threads



PUT notifications
Mark notification threads as read, pinned or unread



GET notifications/new
Check if unread notifications exist



GET notifications/threads/{id}
Get notification thread by ID



PATCH notifications/threads/{id}
Mark notification thread as read by ID



GET repos/{owner}/{repo}/notifications
List users's notification threads on a specific repo



PUT repos/{owner}/{repo}/notifications
Mark notification threads as read, pinned or unread on a specific repo


organization

package


GET packages/{owner}
Gets all packages of an owner



POST packages/{owner}/{type}/{name}/-/link/{repo_name}
Link a package to a repository



POST packages/{owner}/{type}/{name}/-/unlink
Unlink a package from a repository



GET packages/{owner}/{type}/{name}/{version}
Gets a package



DELETE packages/{owner}/{type}/{name}/{version}
Delete a package



GET packages/{owner}/{type}/{name}/{version}/files
Gets all files of a package


issue


GET repos/issues/search
Search for issues across the repositories that the user has access to



GET repos/{owner}/{repo}/issues
List a repository's issues



POST repos/{owner}/{repo}/issues
Create an issue. If using deadline only the date will be taken into account, and time of day ignored.



GET repos/{owner}/{repo}/issues/comments
List all comments in a repository



GET repos/{owner}/{repo}/issues/comments/{id}
Get a comment



DELETE repos/{owner}/{repo}/issues/comments/{id}
Delete a comment



PATCH repos/{owner}/{repo}/issues/comments/{id}
Edit a comment



GET repos/{owner}/{repo}/issues/comments/{id}/assets
List comment's attachments



POST repos/{owner}/{repo}/issues/comments/{id}/assets
Create a comment attachment



GET repos/{owner}/{repo}/issues/comments/{id}/assets/{attachment_id}
Get a comment attachment



DELETE repos/{owner}/{repo}/issues/comments/{id}/assets/{attachment_id}
Delete a comment attachment



PATCH repos/{owner}/{repo}/issues/comments/{id}/assets/{attachment_id}
Edit a comment attachment



GET repos/{owner}/{repo}/issues/comments/{id}/reactions
Get a list of reactions from a comment of an issue



POST repos/{owner}/{repo}/issues/comments/{id}/reactions
Add a reaction to a comment of an issue



DELETE repos/{owner}/{repo}/issues/comments/{id}/reactions
Remove a reaction from a comment of an issue



GET repos/{owner}/{repo}/issues/{index}
Get an issue



DELETE repos/{owner}/{repo}/issues/{index}
Delete an issue



PATCH repos/{owner}/{repo}/issues/{index}
Edit an issue. If using deadline only the date will be taken into account, and time of day ignored.



GET repos/{owner}/{repo}/issues/{index}/assets
List issue's attachments



POST repos/{owner}/{repo}/issues/{index}/assets
Create an issue attachment



GET repos/{owner}/{repo}/issues/{index}/assets/{attachment_id}
Get an issue attachment



DELETE repos/{owner}/{repo}/issues/{index}/assets/{attachment_id}
Delete an issue attachment



PATCH repos/{owner}/{repo}/issues/{index}/assets/{attachment_id}
Edit an issue attachment



GET repos/{owner}/{repo}/issues/{index}/blocks
List issues that are blocked by this issue



POST repos/{owner}/{repo}/issues/{index}/blocks
Block the issue given in the body by the issue in path



DELETE repos/{owner}/{repo}/issues/{index}/blocks
Unblock the issue given in the body by the issue in path



GET repos/{owner}/{repo}/issues/{index}/comments
List all comments on an issue



POST repos/{owner}/{repo}/issues/{index}/comments
Add a comment to an issue



DELETE repos/{owner}/{repo}/issues/{index}/comments/{id}
Delete a comment



PATCH repos/{owner}/{repo}/issues/{index}/comments/{id}
Edit a comment



POST repos/{owner}/{repo}/issues/{index}/deadline
Set an issue deadline. If set to null, the deadline is deleted. If using deadline only the date will be taken into account, and time of day ignored.



GET repos/{owner}/{repo}/issues/{index}/dependencies
List an issue's dependencies, i.e all issues that block this issue.



POST repos/{owner}/{repo}/issues/{index}/dependencies
Make the issue in the url depend on the issue in the form.



DELETE repos/{owner}/{repo}/issues/{index}/dependencies
Remove an issue dependency



GET repos/{owner}/{repo}/issues/{index}/labels
Get an issue's labels



PUT repos/{owner}/{repo}/issues/{index}/labels
Replace an issue's labels



POST repos/{owner}/{repo}/issues/{index}/labels
Add a label to an issue



DELETE repos/{owner}/{repo}/issues/{index}/labels
Remove all labels from an issue



DELETE repos/{owner}/{repo}/issues/{index}/labels/{id}
Remove a label from an issue



POST repos/{owner}/{repo}/issues/{index}/pin
Pin an Issue



DELETE repos/{owner}/{repo}/issues/{index}/pin
Unpin an Issue



PATCH repos/{owner}/{repo}/issues/{index}/pin/{position}
Moves the Pin to the given Position



GET repos/{owner}/{repo}/issues/{index}/reactions
Get a list reactions of an issue



POST repos/{owner}/{repo}/issues/{index}/reactions
Add a reaction to an issue



DELETE repos/{owner}/{repo}/issues/{index}/reactions
Remove a reaction from an issue



DELETE repos/{owner}/{repo}/issues/{index}/stopwatch/delete
Delete an issue's existing stopwatch.



POST repos/{owner}/{repo}/issues/{index}/stopwatch/start
Start stopwatch on an issue.



POST repos/{owner}/{repo}/issues/{index}/stopwatch/stop
Stop an issue's existing stopwatch.



GET repos/{owner}/{repo}/issues/{index}/subscriptions
Get users who subscribed on an issue.



GET repos/{owner}/{repo}/issues/{index}/subscriptions/check
Check if user is subscribed to an issue



PUT repos/{owner}/{repo}/issues/{index}/subscriptions/{user}
Subscribe user to issue



DELETE repos/{owner}/{repo}/issues/{index}/subscriptions/{user}
Unsubscribe user from issue



GET repos/{owner}/{repo}/issues/{index}/timeline
List all comments and events on an issue



GET repos/{owner}/{repo}/issues/{index}/times
List an issue's tracked times



POST repos/{owner}/{repo}/issues/{index}/times
Add tracked time to a issue



DELETE repos/{owner}/{repo}/issues/{index}/times
Reset a tracked time of an issue



DELETE repos/{owner}/{repo}/issues/{index}/times/{id}
Delete specific tracked time



GET repos/{owner}/{repo}/labels
Get all of a repository's labels



POST repos/{owner}/{repo}/labels
Create a label



GET repos/{owner}/{repo}/labels/{id}
Get a single label



DELETE repos/{owner}/{repo}/labels/{id}
Delete a label



PATCH repos/{owner}/{repo}/labels/{id}
Update a label



GET repos/{owner}/{repo}/milestones
Get all of a repository's opened milestones



POST repos/{owner}/{repo}/milestones
Create a milestone



GET repos/{owner}/{repo}/milestones/{id}
Get a milestone



DELETE repos/{owner}/{repo}/milestones/{id}
Delete a milestone



PATCH repos/{owner}/{repo}/milestones/{id}
Update a milestone


repository


POST repos/migrate
Migrate a remote git repository



GET repos/search
Search for repositories



GET repos/{owner}/{repo}
Get a repository



DELETE repos/{owner}/{repo}
Delete a repository



PATCH repos/{owner}/{repo}
Edit a repository's properties. Only fields that are set will be changed.



GET repos/{owner}/{repo}/actions/runners/jobs
Search for repository's action jobs according filter conditions



GET repos/{owner}/{repo}/actions/runners/registration-token
Get a repository's actions runner registration token



GET repos/{owner}/{repo}/actions/secrets
List an repo's actions secrets



PUT repos/{owner}/{repo}/actions/secrets/{secretname}
Create or Update a secret value in a repository



DELETE repos/{owner}/{repo}/actions/secrets/{secretname}
Delete a secret in a repository



GET repos/{owner}/{repo}/actions/tasks
List a repository's action tasks



GET repos/{owner}/{repo}/actions/variables
Get repo-level variables list



GET repos/{owner}/{repo}/actions/variables/{variablename}
Get a repo-level variable



PUT repos/{owner}/{repo}/actions/variables/{variablename}
Update a repo-level variable



POST repos/{owner}/{repo}/actions/variables/{variablename}
Create a repo-level variable



DELETE repos/{owner}/{repo}/actions/variables/{variablename}
Delete a repo-level variable



POST repos/{owner}/{repo}/actions/workflows/{workflowname}/dispatches
Dispatches a workflow



GET repos/{owner}/{repo}/activities/feeds
List a repository's activity feeds



GET repos/{owner}/{repo}/archive/{archive}
Get an archive of a repository



GET repos/{owner}/{repo}/assignees
Return all users that have write access and can be assigned to issues



POST repos/{owner}/{repo}/avatar
Update avatar



DELETE repos/{owner}/{repo}/avatar
Delete avatar



GET repos/{owner}/{repo}/branch_protections
List branch protections for a repository



POST repos/{owner}/{repo}/branch_protections
Create a branch protections for a repository



GET repos/{owner}/{repo}/branch_protections/{name}
Get a specific branch protection for the repository



DELETE repos/{owner}/{repo}/branch_protections/{name}
Delete a specific branch protection for the repository



PATCH repos/{owner}/{repo}/branch_protections/{name}
Edit a branch protections for a repository. Only fields that are set will be changed



GET repos/{owner}/{repo}/branches
List a repository's branches



POST repos/{owner}/{repo}/branches
Create a branch



GET repos/{owner}/{repo}/branches/{branch}
Retrieve a specific branch from a repository, including its effective branch protection



DELETE repos/{owner}/{repo}/branches/{branch}
Delete a specific branch from a repository



PATCH repos/{owner}/{repo}/branches/{branch}
Update a branch



GET repos/{owner}/{repo}/collaborators
List a repository's collaborators



GET repos/{owner}/{repo}/collaborators/{collaborator}
Check if a user is a collaborator of a repository



PUT repos/{owner}/{repo}/collaborators/{collaborator}
Add a collaborator to a repository



DELETE repos/{owner}/{repo}/collaborators/{collaborator}
Delete a collaborator from a repository



GET repos/{owner}/{repo}/collaborators/{collaborator}/permission
Get repository permissions for a user



GET repos/{owner}/{repo}/commits
Get a list of all commits from a repository



GET repos/{owner}/{repo}/commits/{ref}/status
Get a commit's combined status, by branch/tag/commit reference



GET repos/{owner}/{repo}/commits/{ref}/statuses
Get a commit's statuses, by branch/tag/commit reference



GET repos/{owner}/{repo}/commits/{sha}/pull
Get the pull request of the commit



GET repos/{owner}/{repo}/compare/{basehead}
Get commit comparison information



GET repos/{owner}/{repo}/contents
Gets the metadata of all the entries of the root dir



POST repos/{owner}/{repo}/contents
Modify multiple files in a repository



GET repos/{owner}/{repo}/contents/{filepath}
Gets the metadata and contents (if a file) of an entry in a repository, or a list of entries if a dir



PUT repos/{owner}/{repo}/contents/{filepath}
Update a file in a repository



POST repos/{owner}/{repo}/contents/{filepath}
Create a file in a repository



DELETE repos/{owner}/{repo}/contents/{filepath}
Delete a file in a repository



POST repos/{owner}/{repo}/diffpatch
Apply diff patch to repository



GET repos/{owner}/{repo}/editorconfig/{filepath}
Get the EditorConfig definitions of a file in a repository



GET repos/{owner}/{repo}/flags
List a repository's flags



PUT repos/{owner}/{repo}/flags
Replace all flags of a repository



DELETE repos/{owner}/{repo}/flags
Remove all flags from a repository



GET repos/{owner}/{repo}/flags/{flag}
Check if a repository has a given flag



PUT repos/{owner}/{repo}/flags/{flag}
Add a flag to a repository



DELETE repos/{owner}/{repo}/flags/{flag}
Remove a flag from a repository



GET repos/{owner}/{repo}/forks
List a repository's forks



POST repos/{owner}/{repo}/forks
Fork a repository



GET repos/{owner}/{repo}/git/blobs/{sha}
Gets the blob of a repository.



GET repos/{owner}/{repo}/git/commits/{sha}
Get a single commit from a repository



GET repos/{owner}/{repo}/git/commits/{sha}.{diffType}
Get a commit's diff or patch



GET repos/{owner}/{repo}/git/notes/{sha}
Get a note corresponding to a single commit from a repository



POST repos/{owner}/{repo}/git/notes/{sha}
Set a note corresponding to a single commit from a repository



DELETE repos/{owner}/{repo}/git/notes/{sha}
Removes a note corresponding to a single commit from a repository



GET repos/{owner}/{repo}/git/refs
Get specified ref or filtered repository's refs



GET repos/{owner}/{repo}/git/refs/{ref}
Get specified ref or filtered repository's refs



GET repos/{owner}/{repo}/git/tags/{sha}
Gets the tag object of an annotated tag (not lightweight tags)



GET repos/{owner}/{repo}/git/trees/{sha}
Gets the tree of a repository.



GET repos/{owner}/{repo}/hooks
List the hooks in a repository



POST repos/{owner}/{repo}/hooks
Create a hook



GET repos/{owner}/{repo}/hooks/git
List the Git hooks in a repository



GET repos/{owner}/{repo}/hooks/git/{id}
Get a Git hook



DELETE repos/{owner}/{repo}/hooks/git/{id}
Delete a Git hook in a repository



PATCH repos/{owner}/{repo}/hooks/git/{id}
Edit a Git hook in a repository



GET repos/{owner}/{repo}/hooks/{id}
Get a hook



DELETE repos/{owner}/{repo}/hooks/{id}
Delete a hook in a repository



PATCH repos/{owner}/{repo}/hooks/{id}
Edit a hook in a repository



POST repos/{owner}/{repo}/hooks/{id}/tests
Test a push webhook



GET repos/{owner}/{repo}/issue_config
Returns the issue config for a repo



GET repos/{owner}/{repo}/issue_config/validate
Returns the validation information for a issue config



GET repos/{owner}/{repo}/issue_templates
Get available issue templates for a repository



GET repos/{owner}/{repo}/issues/pinned
List a repo's pinned issues



GET repos/{owner}/{repo}/keys
List a repository's keys



POST repos/{owner}/{repo}/keys
Add a key to a repository



GET repos/{owner}/{repo}/keys/{id}
Get a repository's key by id



DELETE repos/{owner}/{repo}/keys/{id}
Delete a key from a repository



GET repos/{owner}/{repo}/languages
Get languages and number of bytes of code written



GET repos/{owner}/{repo}/media/{filepath}
Get a file or it's LFS object from a repository



POST repos/{owner}/{repo}/mirror-sync
Sync a mirrored repository



GET repos/{owner}/{repo}/new_pin_allowed
Returns if new Issue Pins are allowed



GET repos/{owner}/{repo}/pulls
List a repo's pull requests



POST repos/{owner}/{repo}/pulls
Create a pull request



GET repos/{owner}/{repo}/pulls/pinned
List a repo's pinned pull requests



GET repos/{owner}/{repo}/pulls/{base}/{head}
Get a pull request by base and head



GET repos/{owner}/{repo}/pulls/{index}
Get a pull request



PATCH repos/{owner}/{repo}/pulls/{index}
Update a pull request. If using deadline only the date will be taken into account, and time of day ignored.



GET repos/{owner}/{repo}/pulls/{index}.{diffType}
Get a pull request diff or patch



GET repos/{owner}/{repo}/pulls/{index}/commits
Get commits for a pull request



GET repos/{owner}/{repo}/pulls/{index}/files
Get changed files for a pull request



GET repos/{owner}/{repo}/pulls/{index}/merge
Check if a pull request has been merged



POST repos/{owner}/{repo}/pulls/{index}/merge
Merge a pull request



DELETE repos/{owner}/{repo}/pulls/{index}/merge
Cancel the scheduled auto merge for the given pull request



POST repos/{owner}/{repo}/pulls/{index}/requested_reviewers
create review requests for a pull request



DELETE repos/{owner}/{repo}/pulls/{index}/requested_reviewers
cancel review requests for a pull request



GET repos/{owner}/{repo}/pulls/{index}/reviews
List all reviews for a pull request



POST repos/{owner}/{repo}/pulls/{index}/reviews
Create a review to an pull request



GET repos/{owner}/{repo}/pulls/{index}/reviews/{id}
Get a specific review for a pull request



POST repos/{owner}/{repo}/pulls/{index}/reviews/{id}
Submit a pending review to an pull request



DELETE repos/{owner}/{repo}/pulls/{index}/reviews/{id}
Delete a specific review from a pull request



GET repos/{owner}/{repo}/pulls/{index}/reviews/{id}/comments
Get a specific review for a pull request



POST repos/{owner}/{repo}/pulls/{index}/reviews/{id}/comments
Add a new comment to a pull request review



GET repos/{owner}/{repo}/pulls/{index}/reviews/{id}/comments/{comment}
Get a pull review comment



DELETE repos/{owner}/{repo}/pulls/{index}/reviews/{id}/comments/{comment}
Delete a pull review comment



POST repos/{owner}/{repo}/pulls/{index}/reviews/{id}/dismissals
Dismiss a review for a pull request



POST repos/{owner}/{repo}/pulls/{index}/reviews/{id}/undismissals
Cancel to dismiss a review for a pull request



POST repos/{owner}/{repo}/pulls/{index}/update
Merge PR's baseBranch into headBranch



GET repos/{owner}/{repo}/push_mirrors
Get all push mirrors of the repository



POST repos/{owner}/{repo}/push_mirrors
add a push mirror to the repository



POST repos/{owner}/{repo}/push_mirrors-sync
Sync all push mirrored repository



GET repos/{owner}/{repo}/push_mirrors/{name}
Get push mirror of the repository by remoteName



DELETE repos/{owner}/{repo}/push_mirrors/{name}
deletes a push mirror from a repository by remoteName



GET repos/{owner}/{repo}/raw/{filepath}
Get a file from a repository



GET repos/{owner}/{repo}/releases
List a repo's releases



POST repos/{owner}/{repo}/releases
Create a release



GET repos/{owner}/{repo}/releases/latest
Gets the most recent non-prerelease, non-draft release of a repository, sorted by created_at



GET repos/{owner}/{repo}/releases/tags/{tag}
Get a release by tag name



DELETE repos/{owner}/{repo}/releases/tags/{tag}
Delete a release by tag name



GET repos/{owner}/{repo}/releases/{id}
Get a release



DELETE repos/{owner}/{repo}/releases/{id}
Delete a release



PATCH repos/{owner}/{repo}/releases/{id}
Update a release



GET repos/{owner}/{repo}/releases/{id}/assets
List release's attachments



POST repos/{owner}/{repo}/releases/{id}/assets
Create a release attachment



GET repos/{owner}/{repo}/releases/{id}/assets/{attachment_id}
Get a release attachment



DELETE repos/{owner}/{repo}/releases/{id}/assets/{attachment_id}
Delete a release attachment



PATCH repos/{owner}/{repo}/releases/{id}/assets/{attachment_id}
Edit a release attachment



GET repos/{owner}/{repo}/reviewers
Return all users that can be requested to review in this repo



GET repos/{owner}/{repo}/signing-key.gpg
Get signing-key.gpg for given repository



GET repos/{owner}/{repo}/stargazers
List a repo's stargazers



GET repos/{owner}/{repo}/statuses/{sha}
Get a commit's statuses



POST repos/{owner}/{repo}/statuses/{sha}
Create a commit status



GET repos/{owner}/{repo}/subscribers
List a repo's watchers



GET repos/{owner}/{repo}/subscription
Check if the current user is watching a repo



PUT repos/{owner}/{repo}/subscription
Watch a repo



DELETE repos/{owner}/{repo}/subscription
Unwatch a repo



GET repos/{owner}/{repo}/tag_protections
List tag protections for a repository



POST repos/{owner}/{repo}/tag_protections
Create a tag protections for a repository



GET repos/{owner}/{repo}/tag_protections/{id}
Get a specific tag protection for the repository



DELETE repos/{owner}/{repo}/tag_protections/{id}
Delete a specific tag protection for the repository



PATCH repos/{owner}/{repo}/tag_protections/{id}
Edit a tag protections for a repository. Only fields that are set will be changed



GET repos/{owner}/{repo}/tags
List a repository's tags



POST repos/{owner}/{repo}/tags
Create a new git tag in a repository



GET repos/{owner}/{repo}/tags/{tag}
Get the tag of a repository by tag name



DELETE repos/{owner}/{repo}/tags/{tag}
Delete a repository's tag by name



GET repos/{owner}/{repo}/teams
List a repository's teams



GET /repos/{owner}/{repo}/teams/{team}
Check if a team is assigned to a repository



PUT /repos/{owner}/{repo}/teams/{team}
Add a team to a repository



DELETE /repos/{owner}/{repo}/teams/{team}
Delete a team from a repository



GET /repos/{owner}/{repo}/times
List a repo's tracked times



GET /repos/{owner}/{repo}/times/{user}
List a user's tracked times in a repo



GET /repos/{owner}/{repo}/topics
Get list of topics that a repository has



PUT /repos/{owner}/{repo}/topics
Replace list of topics for a repository



PUT /repos/{owner}/{repo}/topics/{topic}
Add a topic to a repository



DELETE /repos/{owner}/{repo}/topics/{topic}
Delete a topic from a repository



POST /repos/{owner}/{repo}/transfer
Transfer a repo ownership



POST /repos/{owner}/{repo}/transfer/accept
Accept a repo transfer



POST /repos/{owner}/{repo}/transfer/reject
Reject a repo transfer



POST /repos/{owner}/{repo}/wiki/new
Create a wiki page



GET /repos/{owner}/{repo}/wiki/page/{pageName}
Get a wiki page



DELETE /repos/{owner}/{repo}/wiki/page/{pageName}
Delete a wiki page



PATCH /repos/{owner}/{repo}/wiki/page/{pageName}
Edit a wiki page



GET /repos/{owner}/{repo}/wiki/pages
Get all wiki pages



GET /repos/{owner}/{repo}/wiki/revisions/{pageName}
Get revisions of a wiki page



POST /repos/{template_owner}/{template_repo}/generate
Create a repository using a template



GET /repositories/{id}
Get a repository by id



GET /topics/search
search topics via keyword



POST /user/repos
Create a repository