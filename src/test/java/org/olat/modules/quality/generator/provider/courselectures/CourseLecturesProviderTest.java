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
package org.olat.modules.quality.generator.provider.courselectures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olat.test.JunitTestHelper.random;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.Test;
import org.olat.basesecurity.OrganisationService;
import org.olat.core.commons.persistence.DB;
import org.olat.core.id.Identity;
import org.olat.core.id.Organisation;
import org.olat.modules.curriculum.Curriculum;
import org.olat.modules.curriculum.CurriculumElement;
import org.olat.modules.curriculum.CurriculumElementRef;
import org.olat.modules.curriculum.CurriculumService;
import org.olat.modules.lecture.LectureBlock;
import org.olat.modules.lecture.LectureService;
import org.olat.modules.quality.QualityDataCollection;
import org.olat.modules.quality.QualityService;
import org.olat.modules.quality.generator.QualityGenerator;
import org.olat.modules.quality.generator.QualityGeneratorConfigs;
import org.olat.modules.quality.generator.QualityGeneratorService;
import org.olat.modules.quality.generator.manager.QualityGeneratorConfigsImpl;
import org.olat.modules.quality.generator.ui.CurriculumElementWhiteListController;
import org.olat.repository.RepositoryEntry;
import org.olat.repository.RepositoryService;
import org.olat.test.JunitTestHelper;
import org.olat.test.OlatTestCase;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 20 Dec 2019<br>
 * @author uhensler, urs.hensler@frentix.com, http://www.frentix.com
 *
 */
public class CourseLecturesProviderTest extends OlatTestCase {

	@Autowired
	private DB dbInstance;
	@Autowired
	private QualityGeneratorService generatorService;
	@Autowired
	private QualityService qualityService;
	@Autowired
	private OrganisationService organisationService;
	@Autowired
	private LectureService lectureService;
	@Autowired
	private RepositoryService repositoryService;
	@Autowired
	private CurriculumService curriculumService;
	
	@Autowired
	private CourseLecturesProvider sut;
	
	@Test
	public void shouldCopyOrganisationsOfCourseToDataCollection() {
		Date startDate = new GregorianCalendar(2010, 6, 3).getTime();
		Date startEnd = new GregorianCalendar(2010, 6, 4).getTime();
		RepositoryEntry courseEntry = createCourseWithThreeLectures(startDate, startEnd);
		Organisation defaultOrganisation = organisationService.getDefaultOrganisation();
		repositoryService.removeOrganisation(courseEntry, defaultOrganisation);
		Organisation courseOrganisation1 = createOrganisation(defaultOrganisation);
		repositoryService.addOrganisation(courseEntry, courseOrganisation1);
		Organisation courseOrganisation2 = createOrganisation(defaultOrganisation);
		repositoryService.addOrganisation(courseEntry, courseOrganisation2);
		CurriculumElement element = createCurriculumElement();
		curriculumService.addRepositoryEntry(element, courseEntry, false);
		
		QualityGenerator generator = createGeneratorInDefaultOrganisation();
		String durationDays = "10";
		QualityGeneratorConfigs configs = createAfterSecondLectureConfigs(generator, durationDays, element);
		
		Date lastRun = new GregorianCalendar(2010, 6, 1).getTime();
		Date now = new GregorianCalendar(2010, 6, 13).getTime();
		
		List<QualityDataCollection> generated = sut.generate(generator, configs, lastRun, now);
		dbInstance.commitAndCloseSession();
		
		QualityDataCollection dataCollection = generated.get(0);
		List<Organisation> organisations = qualityService.loadDataCollectionOrganisations(dataCollection);
		assertThat(organisations)
				.containsExactlyInAnyOrder(courseOrganisation1, courseOrganisation2)
				.doesNotContain(defaultOrganisation);
	}

	private RepositoryEntry createCourseWithThreeLectures(Date startDate, Date endDate) {
		Identity initialAuthor = JunitTestHelper.createAndPersistIdentityAsAuthor(JunitTestHelper.random());
		RepositoryEntry courseEntry = JunitTestHelper.deployBasicCourse(initialAuthor);
		LectureBlock lectureBlock = lectureService.createLectureBlock(courseEntry);
		lectureBlock.setPlannedLecturesNumber(3);
		lectureBlock.setStartDate(startDate);
		lectureBlock.setEndDate(endDate);
		lectureService.save(lectureBlock, null);
		lectureService.addTeacher(lectureBlock, initialAuthor);
		
		dbInstance.commitAndCloseSession();
		return courseEntry;
	}

	private QualityGeneratorConfigs createAfterSecondLectureConfigs(QualityGenerator generator, String durationDays,
			CurriculumElementRef curriculumElementRef) {
		QualityGeneratorConfigs configs = new QualityGeneratorConfigsImpl(generator);
		configs.setValue(CourseLecturesProvider.CONFIG_KEY_TITLE, "DATA_COLLECTION_TITLE");
		configs.setValue(CourseLecturesProvider.CONFIG_KEY_ROLES, "coach");
		configs.setValue(CourseLecturesProvider.CONFIG_KEY_TOTAL_LECTURES_MIN, "0");
		configs.setValue(CourseLecturesProvider.CONFIG_KEY_TOTAL_LECTURES_MAX, "10000");
		configs.setValue(CourseLecturesProvider.CONFIG_KEY_SURVEY_LECTURE, CourseLecturesProvider.CONFIG_KEY_SURVEY_LECTURE_LAST);
		configs.setValue(CourseLecturesProvider.CONFIG_KEY_DURATION_DAYS, durationDays);
		// Restrict to a single curriculum element
		CurriculumElementWhiteListController.setCurriculumElementRefs(configs, Collections.singletonList(curriculumElementRef));
		dbInstance.commitAndCloseSession();
		return configs;
	}

	private QualityGenerator createGeneratorInDefaultOrganisation() {
		Organisation organisation = organisationService.getDefaultOrganisation();
		Collection<Organisation> organisations = Collections.singletonList(organisation);
		QualityGenerator generator = generatorService.createGenerator(sut.getType(), organisations);
		RepositoryEntry formEntry = JunitTestHelper.createAndPersistRepositoryEntry();
		generator.setFormEntry(formEntry);
		generatorService.updateGenerator(generator);
		dbInstance.commitAndCloseSession();
		return generator;
	}

	private Organisation createOrganisation(Organisation parent) {
		Organisation organisation = organisationService.createOrganisation(random(), random(), random(), parent, null);
		dbInstance.commitAndCloseSession();
		return organisation;
	}

	private CurriculumElement createCurriculumElement() {
		Organisation organisation = organisationService.getDefaultOrganisation();
		Curriculum curriculum = curriculumService.createCurriculum(random(), random(), random(), organisation);
		CurriculumElement curriculumElement = curriculumService.createCurriculumElement(random(), random(), null, null,
				null, null, null, null, null, curriculum);
		dbInstance.commitAndCloseSession();
		return curriculumElement;
	}

}
