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

import com.gitblit.manager.IManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.Serializable;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Jabber implements IManager {

	private static Jabber instance;

	final Logger log = LoggerFactory.getLogger(getClass());

	final IRuntimeManager runtimeManager;

	private XMPPConnection conn;

	private Map<String, MultiUserChat> chats;

	final ExecutorService taskPool;

	public static void init(IRuntimeManager manager) {
		if (instance == null) {
			instance = new Jabber(manager);
		}
	}

	public static Jabber instance() {
		return instance;
	}

	Jabber(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
		this.taskPool = Executors.newCachedThreadPool();
	}

	@Override
	public Jabber start() {

		try {

			String domain = runtimeManager.getSettings().getString(Plugin.SETTING_DOMAIN, null);
			String host = runtimeManager.getSettings().getString(Plugin.SETTING_HOST, null);
				
			ConnectionConfiguration cfg;
			if (domain != null) {
				cfg = new ConnectionConfiguration(domain);				
			} else if (host != null) {				
				int port = 5222;
				String portString = runtimeManager.getSettings().getString(Plugin.SETTING_PORT, null);
				if (portString != null) {
					try {
						port = Integer.parseInt(portString);
					} catch (NumberFormatException e) {
						log.warn("Ignoring invalid port " + portString);
					}	
				}				
				cfg = new ConnectionConfiguration(host, port);
			} else {
				log.error("Need a valid " + Plugin.SETTING_DOMAIN + " or " + Plugin.SETTING_HOST + " configuration");
				return this;
			}

			boolean acceptAllCerts = runtimeManager.getSettings().getBoolean(Plugin.SETTING_ACCEPT_ALL_CERTS, false);
			if(acceptAllCerts) {
				SSLContext context = SSLContext.getInstance("TLS");

				X509TrustManager tm = new X509TrustManager() {
					@Override
					public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
					}
					@Override
					public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
					}
					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
				};
				context.init(null, new TrustManager[] { tm }, new SecureRandom());
				cfg.setCustomSSLContext(context);
				cfg.setHostnameVerifier(new HostnameVerifier() {
					@Override
					public boolean verify(String arg0, SSLSession arg1) {
						return true;
					}
				});
			}

			conn = new XMPPTCPConnection(cfg);
			conn.connect();

			String username = runtimeManager.getSettings().getString(Plugin.SETTING_USERNAME, null);
			String password = runtimeManager.getSettings().getString(Plugin.SETTING_PASSWORD, null);
			conn.login(username, password);

			log.info("Connected to Jabber: " + conn.getUser());

			chats = new TreeMap<>();

			String room = runtimeManager.getSettings().getString(Plugin.SETTING_DEFAULT_ROOM, null);
			String nickname = runtimeManager.getSettings().getString(Plugin.SETTING_NICKNAME, username);

			MultiUserChat chat = new MultiUserChat(conn, room);
			chat.createOrJoin(nickname);

			chats.put(room, chat);

		} catch (SmackException | IOException | XMPPException | NoSuchAlgorithmException | KeyManagementException e) {
			log.error("Failed to connect to jabber server", e);
		}

		return this;
	}

	@Override
	public Jabber stop() {
        this.taskPool.shutdown();
		try {
			if (conn != null) {
				conn.disconnect();
			}
		} catch (SmackException e) {
			log.error("Failed to connect to jabber server", e);
		}

		return this;
	}

	/**
	 * Returns true if the repository can be posted to Jabber chat room.
	 *
	 * @param repository
	 * @return true if the repository can be posted to Jabber chat room.
	 */
	public boolean shallPost(RepositoryModel repository) {
		boolean postPersonalRepos = runtimeManager.getSettings().getBoolean(Plugin.SETTING_POST_PERSONAL_REPOS, false);
		return !(repository.isPersonalRepository() && !postPersonalRepos);
	}

	/**
	 * Optionally sets the room of the message based on the repository.
	 *
	 * @param repository
	 * @param message
	 */
	public void setRoom(RepositoryModel repository, Message message) {
		boolean useProjectRooms = runtimeManager.getSettings().getBoolean(Plugin.SETTING_USE_PROJECT_ROOMS, false);
		if (!useProjectRooms) {
			return;
		}

		log.info("Configured to use project rooms. Project name is " + repository.name);

		if (StringUtils.isEmpty(repository.name)) {
			return;
		}

		String defaultRoom = runtimeManager.getSettings().getString(Plugin.SETTING_DEFAULT_ROOM, null);
		String roomKey = String.format(Plugin.SETTING_PROJECT_ROOM, repository.name).replaceAll(".git$", "");
		String projectRoom = runtimeManager.getSettings().getString(roomKey, null);

		log.info("Found projectRoom " + projectRoom + " via key " + roomKey);

		if (!StringUtils.isEmpty(projectRoom)) {
			message.setRoom(projectRoom);
		} else {
			message.setRoom(defaultRoom);
		}
	}

    /**
     * Asynchronously send a message.
     *
     * @param message
     * @throws IOException
     */
    public void sendAsync(final Message message) {
        taskPool.submit(new JabberTask(this, message));
    }

    /**
	 * Send a message.
	 *
	 * @param message
	 * @throws IOException
	 */
	public void send(Message message) throws IOException {

		String room = message.getRoom();

		if (StringUtils.isEmpty(room)) {
			// default room
			room = runtimeManager.getSettings().getString(Plugin.SETTING_DEFAULT_ROOM, null);
		}

		try {
			MultiUserChat chat = chats.get(room);
			if (chat == null) {
				String username = runtimeManager.getSettings().getString(Plugin.SETTING_USERNAME, null);
				String nickname = runtimeManager.getSettings().getString(Plugin.SETTING_NICKNAME, username);

				chat = new MultiUserChat(conn, room);
				chat.createOrJoin(nickname);

				chats.put(room, chat);
			}

			if (message.getHtml() == null) {
				log.info("Send text message [" + message.getMessage() + " to room [" + room + "]");
	 			chat.sendMessage(message.getMessage());
			} else {
				org.jivesoftware.smack.packet.Message msg = chat.createMessage();

				msg.setBody(message.getMessage());

				XHTMLManager.addBody(msg, message.getHtml());

				log.info("Sending XML [" + msg.toXML().toString() + "]");

				chat.sendMessage(msg);
			}
		} catch (SmackException | XMPPException e) {
			log.error("Failed to send message to jabber server", e);
		}

	}

    private static class JabberTask implements Serializable, Callable<Boolean> {

        private static final long serialVersionUID = 1L;

        final Logger log = LoggerFactory.getLogger(getClass());
        final Jabber jabber;
        final Message message;

        public JabberTask(Jabber slacker, Message message) {
            this.jabber = slacker;
            this.message = message;
        }

        @Override
        public Boolean call() {
            try {
                jabber.send(message);
                return true;
            } catch (IOException e) {
                log.error("Failed to send asynchronously to Jabber!", e);
            }
            return false;
        }
    }

}
