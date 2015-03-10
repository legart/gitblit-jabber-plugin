/*
 * Copyright 2014 Michael Legart.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.plugin.jabber;

import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

import com.gitblit.extensions.GitblitPlugin;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.servlet.GitblitContext;

public class Plugin extends GitblitPlugin {

	public static final String SETTING_DEFAULT_ROOM = "jabber.defaultRoom";

    public static final String SETTING_USE_PROJECT_ROOMS = "jabber.useProjectRooms";

	public static final String SETTING_PROJECT_ROOM = "jabber.projectRoom.%s";

	public static final String SETTING_POST_PERSONAL_REPOS = "jabber.postPersonalRepos";

	public static final String SETTING_POST_TICKETS = "jabber.postTickets";

	public static final String SETTING_POST_TICKET_COMMENTS = "jabber.postTicketComments";

	public static final String SETTING_POST_BRANCHES = "jabber.postBranches";

	public static final String SETTING_DOMAIN = "jabber.domain";

	public static final String SETTING_HOST = "jabber.host";

	public static final String SETTING_PORT = "jabber.port";

	public static final String SETTING_ACCEPT_ALL_CERTS = "jabber.acceptAllCerts";

	public static final String SETTING_POST_TAGS = "jabber.postTags";

	public static final String SETTING_USERNAME = "jabber.username";

	public static final String SETTING_NICKNAME = "jabber.nickname";

	public static final String SETTING_PASSWORD = "jabber.password";

	public Plugin(PluginWrapper wrapper) {
		super(wrapper);

		IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		Jabber.init(runtimeManager);
	}

	@Override
	public void start() {
		Jabber.instance().start();
		log.debug("{} STARTED.", getWrapper().getPluginId());
	}

	@Override
	public void stop() {
		Jabber.instance().stop();
		log.debug("{} STOPPED.", getWrapper().getPluginId());
	}

	@Override
	public void onInstall() {
		log.debug("{} INSTALLED.", getWrapper().getPluginId());
	}

	@Override
	public void onUpgrade(Version oldVersion) {
		log.debug("{} UPGRADED from {}.", getWrapper().getPluginId(), oldVersion);
	}

	@Override
	public void onUninstall() {
		log.debug("{} UNINSTALLED.", getWrapper().getPluginId());
	}
}
