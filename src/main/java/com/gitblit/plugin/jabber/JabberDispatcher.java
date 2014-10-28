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

import java.io.IOException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.manager.IRuntimeManager;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.transport.ssh.commands.UsageExample;
import com.gitblit.transport.ssh.commands.UsageExamples;
import com.gitblit.utils.StringUtils;

@Extension
@CommandMetaData(name = "jabber", description = "Jabber commands")
public class JabberDispatcher extends DispatchCommand {

	@Override
	protected void setup() {
		boolean canAdmin = getContext().getClient().getUser().canAdmin();
		if (canAdmin) {
			register(TestCommand.class);
		}
	}

	@CommandMetaData(name = "test", description = "Post a test message")
	@UsageExamples(examples = {
			@UsageExample(syntax = "${cmd}", description = "Posts a test message to the default chat room"),
			@UsageExample(syntax = "${cmd} myRoom", description = "Posts a test message to myRoom")
	})
	public static class TestCommand extends SshCommand {

		@Argument(index = 0, metaVar = "ROOM", usage = "Destination Room for message")
		String room;

		/**
		 * Post a test message
		 */
		@Override
		public void run() throws Failure {
		    Message message = Message.text("Test message sent from Gitblit");
		    if (!StringUtils.isEmpty(room)) {
		    	message.room(room);
		    }

			try {
				IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
				Jabber.init(runtimeManager);
				Jabber.instance().send(message);
			} catch (IOException e) {
			    throw new Failure(1, e.getMessage(), e);
			}
		}
	}

}
