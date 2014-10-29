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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.manager.IManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;

import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.XMPPConnection;

import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class Jabber implements IManager {

	private static Jabber instance;

	final Logger log = LoggerFactory.getLogger(getClass());

	final IRuntimeManager runtimeManager;

	private XMPPConnection conn;

	private Map<String, MultiUserChat> chats;

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
	}

	@Override
	public Jabber start() {

		try {

			String domain = runtimeManager.getSettings().getString(Plugin.SETTING_DOMAIN, "jabber.org");
			ConnectionConfiguration cfg = new ConnectionConfiguration(domain);

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

			chats = new TreeMap<String, MultiUserChat>();

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
		if (repository.isPersonalRepository() && !postPersonalRepos) {
			return false;
		}
		return true;
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
				String nickname = runtimeManager.getSettings().getString(Plugin.SETTING_DEFAULT_ROOM, username);

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

}
