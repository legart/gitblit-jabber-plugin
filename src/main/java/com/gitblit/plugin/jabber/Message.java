/**
 * Copyright (C) 2014 Michael Legart.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.plugin.jabber;

public class Message {

	private String message;
	private String html;

	private transient String room;

	Message() {
	}

	public static Message create(String message, String html) {
		return new Message(message, html);
	}

	public static Message text(String message) {
        return new Message(message);
    }

	public static Message html(String html) {
		return new Message(html);
	}

	public Message(String message) {
		this.message = message;
	}

	public Message(String message, String html) {
		this.message = message;
		this.html = html;
	}

	public Message message(String message) {
		setMessage(message);
		return this;
	}

	public Message room(String room) {
		setRoom(room);
		return this;
	}

	public String getMessage() {
		return message;
	}

	public String getHtml() {
		return html;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void setHtml(String html) {
		this.html = html;
	}

	public String getRoom() {
		return room;
	}

	public void setRoom(String room) {
		this.room = room;
	}
}
