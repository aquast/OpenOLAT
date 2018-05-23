/**
 * <a href="http://www.openolat.org">
 * OpenOLAT - Online Learning and Training</a><br>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); <br>
 * you may not use this file except in compliance with the License.<br>
 * You may obtain a copy of the License at the
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache homepage</a>
 * <p>
 * Unless required by applicable law or agreed to in writing,<br>
 * software distributed under the License is distributed on an "AS IS" BASIS, <br>
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
 * See the License for the specific language governing permissions and <br>
 * limitations under the License.
 * <p>
 * Initial code contributed and copyrighted by<br>
 * frentix GmbH, http://www.frentix.com
 * <p>
 */
package org.olat.modules.forms.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.olat.core.commons.persistence.DB;
import org.olat.modules.forms.EvaluationFormParticipation;
import org.olat.modules.forms.EvaluationFormSession;
import org.olat.modules.forms.EvaluationFormSessionRef;
import org.olat.modules.forms.EvaluationFormSessionStatus;
import org.olat.modules.forms.EvaluationFormSurvey;
import org.olat.test.OlatTestCase;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 12 déc. 2016<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class EvaluationFormSessionDAOTest extends OlatTestCase {
	
	@Autowired
	private DB dbInstance;
	@Autowired
	private EvaluationFormSessionDAO sut;
	@Autowired
	private EvaluationFormTestsHelper evaTestHelper;
	
	@Before
	public void cleanUp() {
		evaTestHelper.deleteAll();
	}
	
	@Test
	public void shouldCreateSession() {
		EvaluationFormParticipation participation = evaTestHelper.createParticipation();
		dbInstance.commit();
		
		EvaluationFormSession session = sut.createSession(participation);
		dbInstance.commit();
		
		assertThat(session).isNotNull();
		assertThat(session.getKey()).isNotNull();
		assertThat(session.getCreationDate()).isNotNull();
		assertThat(session.getLastModified()).isNotNull();
		assertThat(session.getParticipation()).isEqualTo(participation);
		assertThat(session.getSurvey()).isEqualTo(participation.getSurvey());
	}
	
	@Test
	public void shouldMakeSessionAnonymous() {
		EvaluationFormParticipation participation = evaTestHelper.createParticipation();
		EvaluationFormSession session = sut.createSession(participation);
		dbInstance.commit();
		
		EvaluationFormSession anonymousSession = sut.makeAnonymous(session);
		
		assertThat(anonymousSession.getParticipation()).isNull();
	}

	@Test
	public void shouldLoadByKeys() {
		EvaluationFormSession session1 = evaTestHelper.createSession();
		EvaluationFormSession session2 = evaTestHelper.createSession();
		EvaluationFormSession otherSession = evaTestHelper.createSession();
		dbInstance.commit();
		
		List<EvaluationFormSession> sessions = Arrays.asList(session1, session2);
		List<EvaluationFormSession> loadedSessions = sut.loadSessionsByKey(sessions);
		
		assertThat(loadedSessions)
				.containsExactlyInAnyOrder(session1, session2)
				.doesNotContain(otherSession);
	}
	
	@Test
	public void shouldLoadByParticipation() {
		EvaluationFormParticipation participation = evaTestHelper.createParticipation();
		EvaluationFormSessionRef session = sut.createSession(participation);
		dbInstance.commitAndCloseSession();
		
		EvaluationFormSession loadedSession = sut.loadSessionByParticipation(participation);
		
		assertThat(loadedSession).isNotNull();
		assertThat(loadedSession).isEqualTo(session);
	}
	
	@Test
	public void shouldLoadSessionsBySurvey() {
		EvaluationFormSurvey survey = evaTestHelper.createSurvey();
		EvaluationFormSession session1 = evaTestHelper.createSession(survey);
		sut.changeStatus(session1, EvaluationFormSessionStatus.done);
		EvaluationFormSession session2 = evaTestHelper.createSession(survey);
		sut.changeStatus(session2, EvaluationFormSessionStatus.done);
		EvaluationFormSession openSession = evaTestHelper.createSession(survey);
		EvaluationFormSession otherSession = evaTestHelper.createSession();
		sut.changeStatus(otherSession, EvaluationFormSessionStatus.done);
		dbInstance.commit();
		
		List<EvaluationFormSession> loadedSessions = sut.loadSessionsBySurvey(survey, EvaluationFormSessionStatus.done);
		
		assertThat(loadedSessions)
				.containsExactlyInAnyOrder(session1, session2)
				.doesNotContain(openSession, otherSession);
	}
	
	@Test
	public void shouldCheckIfSurveyHasSessions() {
		EvaluationFormParticipation participation = evaTestHelper.createParticipation();
		EvaluationFormSurvey survey = participation.getSurvey();
		sut.createSession(participation);
		dbInstance.commitAndCloseSession();
		
		boolean hasSessions = sut.hasSessions(survey);
		
		assertThat(hasSessions).isTrue();
	}
	
	@Test
	public void shouldCheckIfSurveyHasNoSessions() {
		EvaluationFormSurvey survey = evaTestHelper.createSurvey();
		dbInstance.commit();
		
		boolean hasSessions = sut.hasSessions(survey);
		
		assertThat(hasSessions).isFalse();
	}
	
	@Test
	public void shouldCheckIfFormHasSessions() {
		EvaluationFormParticipation participation = evaTestHelper.createParticipation();
		EvaluationFormSurvey survey = participation.getSurvey();
		sut.createSession(participation);
		dbInstance.commitAndCloseSession();
		
		boolean hasSessions = sut.hasSessions(survey.getFormEntry());
		
		assertThat(hasSessions).isTrue();
	}
	
	@Test
	public void shouldCheckIfFormHasNoSessions() {
		EvaluationFormSurvey survey = evaTestHelper.createSurvey();
		dbInstance.commit();
		
		boolean hasSessions = sut.hasSessions(survey.getFormEntry());
		
		assertThat(hasSessions).isFalse();
	}
	
	@Test
	public void shouldDeleteSessionsOfSurvey() {
		EvaluationFormSurvey survey = evaTestHelper.createSurvey();
		EvaluationFormSession session1 = evaTestHelper.createSession(survey);
		EvaluationFormSession session2 = evaTestHelper.createSession(survey);
		EvaluationFormSession otherSession = evaTestHelper.createSession();
		dbInstance.commit();
		
		sut.deleteSessions(survey);
		dbInstance.commit();
		
		EvaluationFormSession loadedSession1 = sut.loadSessionByParticipation(session1.getParticipation());
		assertThat(loadedSession1).isNull();
		EvaluationFormSession loadedSession2 = sut.loadSessionByParticipation(session2.getParticipation());
		assertThat(loadedSession2).isNull();
		EvaluationFormSession loadedOtherSession = sut.loadSessionByParticipation(otherSession.getParticipation());
		assertThat(loadedOtherSession).isEqualTo(otherSession);
	}
	
}
