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
package org.olat.upgrade;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.olat.commons.info.notification.InfoSubscription;
import org.olat.core.commons.persistence.DB;
import org.olat.core.commons.persistence.QueryBuilder;
import org.olat.core.commons.services.notifications.NotificationsManager;
import org.olat.core.commons.services.notifications.Publisher;
import org.olat.core.commons.services.notifications.Subscriber;
import org.olat.core.commons.services.notifications.model.SubscriberImpl;
import org.olat.core.id.Identity;
import org.olat.core.id.Organisation;
import org.olat.core.logging.Tracing;
import org.olat.core.util.prefs.Preferences;
import org.olat.core.util.prefs.db.DbStorage;
import org.olat.modules.quality.QualityDataCollection;
import org.olat.modules.quality.QualityService;
import org.olat.properties.Property;
import org.olat.repository.RepositoryService;
import org.olat.repository.model.RepositoryEntryRefImpl;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 20 déc. 2019<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class OLATUpgrade_14_2_0 extends OLATUpgrade {

	private static final Logger log = Tracing.createLoggerFor(OLATUpgrade_14_2_0.class);
	
	private static final int BATCH_SIZE = 100;
	
	private static final String VERSION = "OLAT_14.2.0";
	private static final String TRANSFER_INFO_NOT_DESIRED = "TRANSFER INFOS NOTIFICATIONS NOT DESIRED";
	private static final String DATA_COLLECTION_ORGANISATIONS = "DATA COLLECTION ORGANISATIONS";
	
	@Autowired
	private DB dbInstance;
	@Autowired
	private DbStorage prefsStorage;
	@Autowired
	private NotificationsManager notificationsManager;
	@Autowired
	private QualityService qualityService;
	@Autowired
	private RepositoryService repositoryService;
	@Autowired
	
	public OLATUpgrade_14_2_0() {
		super();
	}
	
	@Override
	public String getVersion() {
		return VERSION;
	}

	@Override
	public boolean doPostSystemInitUpgrade(UpgradeManager upgradeManager) {
		UpgradeHistoryData uhd = upgradeManager.getUpgradesHistory(VERSION);
		if (uhd == null) {
			// has never been called, initialize
			uhd = new UpgradeHistoryData();
		} else if (uhd.isInstallationComplete()) {
			return false;
		}
		
		boolean allOk = true;
		allOk &= migrateInfosNotificationsNotDesired(upgradeManager, uhd);
		allOk &= migrateDataCollectionOrganisations(upgradeManager, uhd);

		uhd.setInstallationComplete(allOk);
		upgradeManager.setUpgradesHistory(uhd, VERSION);
		if(allOk) {
			log.info(Tracing.M_AUDIT, "Finished OLATUpgrade_14_2_0 successfully!");
		} else {
			log.info(Tracing.M_AUDIT, "OLATUpgrade_14_2_0 not finished, try to restart OpenOlat!");
		}
		return allOk;
	}
	
	private boolean migrateInfosNotificationsNotDesired(UpgradeManager upgradeManager, UpgradeHistoryData uhd) {
		boolean allOk = true;
		if (!uhd.getBooleanDataValue(TRANSFER_INFO_NOT_DESIRED)) {
			try {
				int counter = 0;
				List<Property> preferences;
				do {
					preferences = getPreferences(counter, BATCH_SIZE);
					for(Property preference:preferences) {
						migrateInfosNotificationsNotDesired(preference);
					}
					counter += preferences.size();
					log.info(Tracing.M_AUDIT, "Preferences processed: {}, total questions processed ({})", preferences.size(), counter);
					dbInstance.commitAndCloseSession();
				} while(preferences.size() == BATCH_SIZE);
			} catch (Exception e) {
				log.error("", e);
				allOk = false;
			}
			
			uhd.setBooleanDataValue(TRANSFER_INFO_NOT_DESIRED, allOk);
			upgradeManager.setUpgradesHistory(uhd, VERSION);
		}
		return allOk;
	}
	
	private void migrateInfosNotificationsNotDesired(Property preference) {
		try {
			Identity identity = preference.getIdentity();
			Preferences prefs = prefsStorage.getPreferencesForProperty(identity, preference);
			if(prefs != null) {
				@SuppressWarnings("unchecked")
				List<String> infoSubscriptions = (List<String>)prefs.get(InfoSubscription.class, "notdesired");
				if(infoSubscriptions != null && !infoSubscriptions.isEmpty()) {
					for(String infoSubscription:infoSubscriptions) {
						migrateInfosNotificationsNotDesired(identity, infoSubscription);
					}
				}
			}
		} catch(Exception e) {
			log.error("", e);
		}
	}
	
	private void migrateInfosNotificationsNotDesired(Identity identity, String infoSubscription) {
		try {
			List<Publisher> publishers = getPublisher(infoSubscription);
			for(Publisher publisher:publishers) {
				Subscriber subscriber = notificationsManager.getSubscriber(identity, publisher);
				if(subscriber == null || !subscriber.isEnabled()) {
					SubscriberImpl sub = new SubscriberImpl(publisher, identity);
					sub.setEnabled(false);
					sub.setCreationDate(new Date());
					sub.setLastModified(sub.getLastModified());
					dbInstance.getCurrentEntityManager().persist(sub);
					dbInstance.commit();
				}	
			}
		} catch (Exception e) {
			log.error("", e);
			dbInstance.rollbackAndCloseSession();
		}
	}
	
	private List<Property> getPreferences(int firstResult, int maxResults) {
		StringBuilder sb = new StringBuilder();
		sb.append("select p from ").append(Property.class.getName()).append(" as p")
		  .append(" inner join fetch p.identity as ident")
		  .append(" where p.name=:name")
		  .append(" order by p.key");
		return dbInstance.getCurrentEntityManager()
				.createQuery(sb.toString(), Property.class)
				.setFirstResult(firstResult)
				.setMaxResults(maxResults).setParameter("name", "v2guipreferences")
				.getResultList();
	}
	
	private List<Publisher> getPublisher(String businessPath) {
		String q = "select pub from notipublisher pub where pub.businessPath=:businessPath";
		return dbInstance.getCurrentEntityManager()
				.createQuery(q, Publisher.class)
				.setParameter("businessPath", businessPath)
				.getResultList();
	}
	
	private boolean migrateDataCollectionOrganisations(UpgradeManager upgradeManager, UpgradeHistoryData uhd) {
		boolean allOk = true;
		if (!uhd.getBooleanDataValue(DATA_COLLECTION_ORGANISATIONS)) {
			try {
				Collection<QualityDataCollection> dataColletions = getGeneratedDataCollections();
				log.info("Migraton of organisations of {} data collections started.", dataColletions.size());
				for (QualityDataCollection dataCollection : dataColletions) {
					migrateDataCollectionOrganisations(dataCollection);
				}
				log.info("Data collection organisations migrated.");
			} catch (Exception e) {
				log.error("", e);
				allOk = false;
			}
			uhd.setBooleanDataValue(DATA_COLLECTION_ORGANISATIONS, allOk);
			upgradeManager.setUpgradesHistory(uhd, VERSION);
		}
		return allOk;
	}

	private Collection<QualityDataCollection> getGeneratedDataCollections() {
		QueryBuilder sb = new QueryBuilder();
		sb.append("select collection");
		sb.append("  from qualitydatacollection as collection");
		sb.append("       inner join fetch collection.generator generator");
		sb.append("        left join fetch collection.topicCurriculumElement curriculumElement");
		sb.append("        left join fetch curriculumElement.curriculum curriculum");
		sb.append("        left join fetch curriculum.organisation curOrg");
		sb.and().append("collection.generator.key is not null");
		
		return dbInstance.getCurrentEntityManager()
				.createQuery(sb.toString(), QualityDataCollection.class)
				.getResultList();
	}
	
	private void migrateDataCollectionOrganisations(QualityDataCollection dataCollection) {
		log.info("Migration of orgainsations of data collection started: {}", dataCollection);
	
		String generatorType = dataCollection.getGenerator().getType();
		switch (generatorType) {
		case "course-provider":
			mmigrateCourseProvider(dataCollection);
			break;
		case "course-lecture":
		case "course-lecture-followup":
			mmigrateCourseLectureProvider(dataCollection);
			break;
		case "curriculum-element-provider":
			mmigrateCurriculumElementProvider(dataCollection);
			break;
		default:
			break;
		}
		
		log.info("Orgainsations of data collection migrated: {}", dataCollection);
		dbInstance.commitAndCloseSession();
	}

	private void mmigrateCourseProvider(QualityDataCollection dataCollection) {
		Long repositoryKey = dataCollection.getTopicRepositoryEntry().getKey();
		List<Organisation> organisations = repositoryService.getOrganisations(() -> repositoryKey);
		qualityService.updateDataCollectionOrganisations(dataCollection, organisations);
	}

	private void mmigrateCourseLectureProvider(QualityDataCollection dataCollection) {
		Long repositoryKey = null;
		if (dataCollection.getTopicIdentity() != null) {
			repositoryKey = dataCollection.getGeneratorProviderKey();
		} else if (dataCollection.getTopicRepositoryEntry() != null) {
			repositoryKey = dataCollection.getTopicRepositoryEntry().getKey();
		}
		
		if (repositoryKey != null) {
			List<Organisation> organisations = repositoryService.getOrganisations(new RepositoryEntryRefImpl(repositoryKey));
			qualityService.updateDataCollectionOrganisations(dataCollection, organisations);
		}
	}

	private void mmigrateCurriculumElementProvider(QualityDataCollection dataCollection) {
		Organisation organisation = dataCollection.getTopicCurriculumElement().getCurriculum().getOrganisation();
		qualityService.updateDataCollectionOrganisations(dataCollection, Collections.singletonList(organisation));
	}
}
