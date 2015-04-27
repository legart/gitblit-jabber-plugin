## Gitblit Jabber plugin

*REQUIRES 1.6.0*

The Gitblit Jabber plugin provides integration with Jabber/XMPP servers.

The plugin sends chat message into a room for branch or tag changes changes.

### Installation

Build the plugin with ant and copy the zip file to your Gitblit plugins
directory.

### Setup

At a bare minimum you'll need these settings configured in `gitblit.properties`:

    jabber.domain = jabber.org
    jabber.username = gitblit
    jabber.password = gitblitXXX
    jabber.defaultRoom = room@server.tld

`jabber.domain` does a DNS SRV record lookup to find the actual host / port setting. If you do not have such a record, you can also specify host and port separately, like this:

	jabber.host = my.host
	jabber.port = 5222
	
There a handful of additional optional settings:

    jabber.nickname = Gitblit
    jabber.acceptAllCerts = true
    jabber.postPersonalRepos = false
    jabber.postBranches = true
    jabber.postTags = true
    jabber.useProjectRooms = false
    jabber.projectRoom.<my repositoryname> = room@server.tld
    jabber.linkToPhabricator = true
    jabber.phabricatorUrl = http://your.phabricator.setup
    jabber.phabricatorCallsign.<my repositoryname> = MYREPONAME

#### jabber.useProjectRooms

*jabber.useProjectRooms* allows you to have the plugin send messages to different
rooms based on repository name. Be aware that these links can only be seen by
users that can receive XHTML-formatted Jabber messages, since adding an additional
text link to the existing plain text would make the output too chatty.

#### jabber.linkToPhabricator

If you have a Phabricator setup which you use for code review, you can enable this 
option to place a small link beside each commit ID to a Phabricator revision.

### Usage

#### Receive Hook

The receive hook is automatic.

#### SSH Commands (optional)

This plugin also provides a generic mechanism to inject test messages into a chat room.  These commands require administrator permissions.

    ssh host jabber test

### Building against a Gitblit RELEASE

    ant && cp build/target/jabber*.zip /path/to/gitblit/plugins

### Building against a Gitblit SNAPSHOT

    /path/to/dev/gitblit/ant installMoxie
    /path/to/dev/jabber/ant && cp build/target/jabber*.zip /path/to/gitblit/plugins
