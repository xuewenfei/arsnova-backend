/*
 * Copyright (C) 2012 THM webMedia
 *
 * This file is part of ARSnova.
 *
 * ARSnova is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ARSnova is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.thm.arsnova.services;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.thm.arsnova.FeedbackStorage;
import de.thm.arsnova.dao.IDatabaseDao;
import de.thm.arsnova.entities.Feedback;
import de.thm.arsnova.entities.Session;
import de.thm.arsnova.entities.User;
import de.thm.arsnova.exceptions.NoContentException;
import de.thm.arsnova.exceptions.NotFoundException;
import de.thm.arsnova.socket.ARSnovaSocketIOServer;

@Service
public class FeedbackService implements IFeedbackService {

	private static final int DEFAULT_SCHEDULER_DELAY = 5000;

	@Autowired
	private ARSnovaSocketIOServer server;

	/**
	 * minutes, after which the feedback is deleted
	 */
	@Value("${feedback.cleanup}")
	private int cleanupFeedbackDelay;

	@Autowired
	private IDatabaseDao databaseDao;

	private FeedbackStorage feedbackStorage;

	public final void setDatabaseDao(final IDatabaseDao newDatabaseDao) {
		databaseDao = newDatabaseDao;
	}

	@PostConstruct
	public void init() {
		feedbackStorage = new FeedbackStorage();
	}

	@Override
	@Scheduled(fixedDelay = DEFAULT_SCHEDULER_DELAY)
	public final void cleanFeedbackVotes() {
		Map<Session, List<User>> deletedFeedbackOfUsersInSession = feedbackStorage.cleanFeedbackVotes(cleanupFeedbackDelay);
		/*
		 * mapping (Session -> Users) is not suitable for web sockets, because we want to sent all affected
		 * sessions to a single user in one go instead of sending multiple messages for each session. Hence,
		 * we need the mapping (User -> Sessions)
		 */
		final Map<User, Set<Session>> affectedSessionsOfUsers = new HashMap<User, Set<Session>>();

		for (Map.Entry<Session, List<User>> entry : deletedFeedbackOfUsersInSession.entrySet()) {
			final Session session = entry.getKey();
			final List<User> users = entry.getValue();
			for (User user : users) {
				Set<Session> affectedSessions;
				if (affectedSessionsOfUsers.containsKey(user)) {
					affectedSessions = affectedSessionsOfUsers.get(user);
				} else {
					affectedSessions = new HashSet<Session>();
				}
				affectedSessions.add(session);
				affectedSessionsOfUsers.put(user, affectedSessions);
			}
		}
		// Send feedback reset event to all affected users
		for (Map.Entry<User, Set<Session>> entry : affectedSessionsOfUsers.entrySet()) {
			final User user = entry.getKey();
			final Set<Session> arsSessions = entry.getValue();
			server.reportDeletedFeedback(user, arsSessions);
		}
		// For each session that has deleted feedback, send the new feedback to all clients
		for (Session session : deletedFeedbackOfUsersInSession.keySet()) {
			server.reportUpdatedFeedbackForSession(session);
		}
	}

	@Override
	public final Feedback getFeedback(final String keyword) {
		final Session session = databaseDao.getSessionFromKeyword(keyword);
		if (session == null) {
			throw new NotFoundException();
		}
		return feedbackStorage.getFeedback(session);
	}

	@Override
	public final int getFeedbackCount(final String keyword) {
		final Feedback feedback = this.getFeedback(keyword);
		final List<Integer> values = feedback.getValues();
		return values.get(Feedback.FEEDBACK_FASTER) + values.get(Feedback.FEEDBACK_OK)
				+ values.get(Feedback.FEEDBACK_SLOWER) + values.get(Feedback.FEEDBACK_AWAY);
	}

	@Override
	public final double getAverageFeedback(final String sessionkey) {
		final Session session = databaseDao.getSessionFromKeyword(sessionkey);
		if (session == null) {
			throw new NotFoundException();
		}
		final Feedback feedback = feedbackStorage.getFeedback(session);
		final List<Integer> values = feedback.getValues();
		final double count = values.get(Feedback.FEEDBACK_FASTER) + values.get(Feedback.FEEDBACK_OK)
				+ values.get(Feedback.FEEDBACK_SLOWER) + values.get(Feedback.FEEDBACK_AWAY);
		final double sum = values.get(Feedback.FEEDBACK_OK) + values.get(Feedback.FEEDBACK_SLOWER) * 2
				+ values.get(Feedback.FEEDBACK_AWAY) * 3;

		if (count == 0) {
			throw new NoContentException();
		}
		return sum / count;
	}

	@Override
	public final long getAverageFeedbackRounded(final String sessionkey) {
		return Math.round(getAverageFeedback(sessionkey));
	}

	@Override
	public final boolean saveFeedback(final String keyword, final int value, final User user) {
		final Session session = databaseDao.getSessionFromKeyword(keyword);
		if (session == null) {
			throw new NotFoundException();
		}
		feedbackStorage.saveFeedback(session, value, user);
		server.reportUpdatedFeedbackForSession(session);
		return true;
	}

	@Override
	public final Integer getMyFeedback(final String keyword, final User user) {
		final Session session = databaseDao.getSessionFromKeyword(keyword);
		if (session == null) {
			throw new NotFoundException();
		}
		return feedbackStorage.getMyFeedback(session, user);
	}
}
