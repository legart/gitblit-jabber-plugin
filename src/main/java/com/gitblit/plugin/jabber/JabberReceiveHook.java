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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.extensions.ReceiveHook;
import com.gitblit.git.GitblitReceivePack;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.utils.StringUtils;

@Extension
public class JabberReceiveHook extends ReceiveHook {

	final Logger log = LoggerFactory.getLogger(getClass());

	final Jabber jabber;

	enum RefType {
		BRANCH, TAG
	}

	public JabberReceiveHook() {
		super();
		IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		Jabber.init(runtimeManager);
    	jabber = Jabber.instance();
	}

	@Override
	public void onPreReceive(GitblitReceivePack receivePack, Collection<ReceiveCommand> commands) {
		// NOOP
	}

	@Override
	public void onPostReceive(GitblitReceivePack receivePack, Collection<ReceiveCommand> commands) {
		if (!shallPost(receivePack, commands)) {
			return;
		}

    	IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		try {
			for (ReceiveCommand cmd : commands) {
				RefType rType;
				if (cmd.getRefName().startsWith(Constants.R_TAGS)) {
					rType = RefType.TAG;
			    	boolean shallPostTag = runtimeManager.getSettings().getBoolean(Plugin.SETTING_POST_TAGS, true);
			    	if (!shallPostTag) {
			    		continue;
			    	}
				} else if (cmd.getRefName().startsWith(Constants.R_HEADS)) {
					rType = RefType.BRANCH;
			    	boolean shallPostBranch = runtimeManager.getSettings().getBoolean(Plugin.SETTING_POST_BRANCHES, true);
			    	if (!shallPostBranch) {
			    		continue;
			    	}
				} else {
					// ignore other refs
					continue;
				}

				switch (cmd.getType()) {
				case CREATE:
					sendCreate(receivePack, cmd, rType);
					break;
				case UPDATE:
					sendUpdate(receivePack, cmd, rType, true);
					break;
				case UPDATE_NONFASTFORWARD:
					sendUpdate(receivePack, cmd, rType, false);
					break;
				case DELETE:
					sendDelete(receivePack, cmd, rType);
					break;
				}
			}
		} catch (IOException e) {
			log.error("Failed to notify Jabber!", e);
		}
	}

	/**
	 * Determine if the ref changes for this repository should be posted to Jabber.
	 *
	 * @param receivePack
	 * @return true if the ref changes should be posted
	 */
	protected boolean shallPost(GitblitReceivePack receivePack, Collection<ReceiveCommand> commands) {
		return jabber.shallPost(receivePack.getRepositoryModel());
	}

	/**
	 * Sends a Jabber chat message when a branch or a tag is created.
	 *
	 * @param receivePack
	 * @param cmd
	 * @param rType
	 */
	protected void sendCreate(GitblitReceivePack receivePack, ReceiveCommand cmd, RefType rType) throws IOException {
		UserModel user = receivePack.getUserModel();
		RepositoryModel repo = receivePack.getRepositoryModel();
		String shortRef = Repository.shortenRefName(cmd.getRefName());
		String repoUrl = getUrl(repo.name, null, null);
		String logUrl = getUrl(repo.name, shortRef, null);

		String msg = String.format("%s has created %s %s %s in %s %s",
				user.getDisplayName(), rType.name().toLowerCase(), logUrl, shortRef, repoUrl, StringUtils.stripDotGit(repo.name));

    	Message message = Message.text(msg);
		jabber.setRoom(receivePack.getRepositoryModel(), message);
		jabber.sendAsync(message);
    }

	/**
	 * Sends a Jabber chat message when a branch or a tag has been updated.
	 *
	 * @param receivePack
	 * @param cmd
	 * @param rType
	 * @param isFF
	 */
	protected void sendUpdate(GitblitReceivePack receivePack, ReceiveCommand cmd, RefType rType, boolean isFF) throws IOException {
		UserModel user = receivePack.getUserModel();
		RepositoryModel repo = receivePack.getRepositoryModel();
		String shortRef = Repository.shortenRefName(cmd.getRefName());
		String repoUrl = getUrl(repo.name, null, null);

		List<RevCommit> commits = null;
		String action;
		String url;
		switch (rType) {
		case TAG:
			// commit link
			url = getUrl(repo.name, null, shortRef);
			action = "MOVED tag";
			break;
		default:
			// log link
			url = getUrl(repo.name, shortRef, null);
			if (isFF) {
				commits = getCommits(receivePack, cmd.getOldId().name(), cmd.getNewId().name());
				if (commits.size() == 1) {
					action = "pushed 1 commit to";
				} else {
					action = String.format("pushed %d commits to", commits.size());
				}
			} else {
				action = "REWRITTEN";
			}
			break;
		}

		StringBuilder sb = new StringBuilder();
		StringBuilder html = new StringBuilder("<body xmlns=\"http://www.w3.org/1999/xhtml\">");

		String msg = String.format("%s has %s %s %s in %s %s",
				user.getDisplayName(), action, url, shortRef, repoUrl, StringUtils.stripDotGit(repo.name));
		sb.append(msg);

		String htmlMsg = String.format("<b>%s</b> has %s <a href=\"%s\">%s</a> in <a href=\"%s\">%s</a>",
                                user.getDisplayName(), action, StringUtils.escapeForHtml(url, false), shortRef, StringUtils.escapeForHtml(repoUrl, false), StringUtils.stripDotGit(repo.name));
		html.append(htmlMsg);

		if (commits != null) {
			// abbreviated commit list
			int shortIdLen = receivePack.getGitblit().getSettings().getInteger(Keys.web.shortCommitIdLength, 6);
			int maxCommits = 5;
			sb.append("\n");
			html.append("<br/><ol>");
			for (int i = 0; i < Math.min(maxCommits, commits.size()); i++) {
				RevCommit commit = commits.get(i);
				String commitUrl = getUrl(repo.name, null, commit.getName());
				String shortId = commit.getName().substring(0, shortIdLen);
				String shortMessage = StringUtils.escapeForHtml(StringUtils.trimString(commit.getShortMessage(), Constants.LEN_SHORTLOG), false);
				String row = String.format("%s %s %s\n",
						commitUrl, shortId, shortMessage);
				sb.append(row);

				String htmlRow = String.format("<li><a href=\"%s\">%s</a> %s</li>\n",
					StringUtils.escapeForHtml(commitUrl, false), shortId, shortMessage);

				html.append(htmlRow);
			}
			html.append("</ol>");

			// compare link
			if (commits.size() > 1) {
				String compareUrl = getUrl(repo.name, cmd.getOldId().getName(), cmd.getNewId().getName());
				String compareText;
				if (commits.size() > maxCommits) {
					int diff = commits.size() - maxCommits;
					if (diff == 1) {
						compareText = "1 more commit";
					} else {
						compareText = String.format("%d more commits", diff);
					}
				} else {
					compareText = String.format("view comparison of these %s commits", commits.size());
				}
				sb.append(String.format("%s %s", compareUrl, compareText));
				html.append(String.format("<a href=\"%s\">%s</a>", StringUtils.escapeForHtml(compareUrl, false), compareText));
			}
		}

		html.append("</body>");

		Message message = Message.create(sb.toString(), html.toString());
		jabber.setRoom(receivePack.getRepositoryModel(), message);
		jabber.sendAsync(message);
	}

	/**
	 * Sends a Jabber chat message when a branch or a tag is deleted.
	 *
	 * @param receivePack
	 * @param cmd
	 * @param rType
	 */
	protected void sendDelete(GitblitReceivePack receivePack, ReceiveCommand cmd, RefType rType) throws IOException {
		UserModel user = receivePack.getUserModel();
		RepositoryModel repo = receivePack.getRepositoryModel();
		String shortRef = Repository.shortenRefName(cmd.getRefName());
		String repoUrl = getUrl(repo.name, null, null);

		String msg = String.format("%s has deleted %s %s from %s %s",
				user.getDisplayName(), rType.name().toLowerCase(), shortRef, repoUrl, StringUtils.stripDotGit(repo.name));

    	Message message = Message.text(msg);
		jabber.setRoom(receivePack.getRepositoryModel(), message);
		jabber.sendAsync(message);
	}

    /**
     * Returns a link appropriate for the push.
     *
     * If both new and old ids are null, the summary page link is returned.
     *
     * @param repo
     * @param oldId
     * @param newId
     * @return a link
     */
    protected String getUrl(String repo, String oldId, String newId) {
    	IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		String canonicalUrl = runtimeManager.getSettings().getString(Keys.web.canonicalUrl, "https://localhost:8443");

		if (oldId == null && newId != null) {
			// create
			final String hrefPattern = "{0}/commit?r={1}&h={2}";
			return MessageFormat.format(hrefPattern, canonicalUrl, repo, newId);
		} else if (oldId != null && newId == null) {
			// log
			final String hrefPattern = "{0}/log?r={1}&h={2}";
			return MessageFormat.format(hrefPattern, canonicalUrl, repo, oldId);
		} else if (oldId != null && newId != null) {
			// update/compare
			final String hrefPattern = "{0}/compare?r={1}&h={2}..{3}";
			return MessageFormat.format(hrefPattern, canonicalUrl, repo, oldId, newId);
		} else if (oldId == null && newId == null) {
			// summary page
			final String hrefPattern = "{0}/summary?r={1}";
			return MessageFormat.format(hrefPattern, canonicalUrl, repo);
		}

		return null;
    }

    private List<RevCommit> getCommits(GitblitReceivePack receivePack, String baseId, String tipId) {
    	List<RevCommit> list = new ArrayList<>();
		RevWalk walk = receivePack.getRevWalk();
		walk.reset();
		walk.sort(RevSort.TOPO);
		try {
			RevCommit tip = walk.parseCommit(receivePack.getRepository().resolve(tipId));
			RevCommit base = walk.parseCommit(receivePack.getRepository().resolve(baseId));
			walk.markStart(tip);
			walk.markUninteresting(base);
			for (;;) {
				RevCommit c = walk.next();
				if (c == null) {
					break;
				}
				list.add(c);
			}
		} catch (IOException e) {
			// Should never happen, the core receive process would have
			// identified the missing object earlier before we got control.
			log.error("failed to get commits", e);
		} finally {
			walk.release();
		}
		return list;
	}
}
