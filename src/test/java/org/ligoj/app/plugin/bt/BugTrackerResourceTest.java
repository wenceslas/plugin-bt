/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.bt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import jakarta.transaction.Transactional;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.dao.SubscriptionRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.bt.dao.BugTrackerConfigurationRepository;
import org.ligoj.app.plugin.bt.dao.BusinessHoursRepository;
import org.ligoj.app.plugin.bt.dao.CalendarRepository;
import org.ligoj.app.plugin.bt.dao.HolidayRepository;
import org.ligoj.app.plugin.bt.dao.SlaRepository;
import org.ligoj.app.plugin.bt.model.BugTrackerConfiguration;
import org.ligoj.app.plugin.bt.model.BusinessHours;
import org.ligoj.app.plugin.bt.model.Calendar;
import org.ligoj.app.plugin.bt.model.Holiday;
import org.ligoj.app.plugin.bt.model.Sla;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link BugTrackerResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class BugTrackerResourceTest extends AbstractAppTest {

	@Autowired
	private BugTrackerResource resource;

	@Autowired
	private BugTrackerConfigurationRepository repository;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private NodeRepository nodeRepository;

	@Autowired
	private HolidayRepository holidayRepository;

	@Autowired
	private SlaRepository slaRepository;

	@Autowired
	private CalendarRepository calendarRepository;

	@Autowired
	private BusinessHoursRepository businessHoursRepository;

	@Autowired
	private IdentifierHelper identifierHelper;

	private int subscription;

	@BeforeEach
	void prepareSubscription() throws IOException {
		persistSystemEntities();
		persistEntities("csv", new Class[]{Calendar.class, Holiday.class, Node.class, Project.class, Subscription.class,
				BugTrackerConfiguration.class, BusinessHours.class, Sla.class}, StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("MDA");
	}

	/**
	 * Return the subscription identifier of MDA. Assumes there is only one
	 * subscription for a service.
	 */
	private int getSubscription(final String project) {
		return getSubscription(project, BugTrackerResource.SERVICE_KEY);
	}

	@Test
	void getConfiguration() throws Exception {

		slaRepository.findBySubscription(subscription).get(0).setTypes("Bug,New Feature");
		slaRepository.findBySubscription(subscription).get(0).setPriorities("Blocker,Critical");
		slaRepository.findBySubscription(subscription).get(0).setResolutions("Fixed,Won't Fix");

		final BugTrackerResource resource = new BugTrackerResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.servicePluginLocator = Mockito.mock(ServicePluginLocator.class);
		final BugTrackerServicePlugin jiraMock = Mockito.mock(BugTrackerServicePlugin.class);
		Mockito.when(jiraMock.getPriorities(subscription)).thenReturn(Collections.singleton("Critical"));
		Mockito.when(jiraMock.getResolutions(subscription)).thenReturn(Collections.singleton("Fixed"));
		Mockito.when(jiraMock.getStatuses(subscription)).thenReturn(Collections.singleton("OPEN"));
		Mockito.when(jiraMock.getTypes(subscription)).thenReturn(Collections.singleton("Bug"));
		Mockito.when(resource.servicePluginLocator.getResourceExpected("service:bt:jira:4", BugTrackerServicePlugin.class))
				.thenReturn(jiraMock);
		final BtConfigurationVo configurationVo = resource.getConfiguration(subscription);

		// Check SLAs
		final List<SlaConfiguration> slas = configurationVo.getSlas();
		Assertions.assertEquals(1, slas.size());
		final SlaConfiguration sla = slas.get(0);
		Assertions.assertEquals("Délais de fermeture", sla.getDescription());
		Assertions.assertEquals("Livraison", sla.getName());
		Assertions.assertTrue(sla.getId() > 0);
		Assertions.assertEquals(1, sla.getPause().size());
		Assertions.assertEquals("RESOLVED", sla.getPause().get(0));
		Assertions.assertEquals("OPEN", sla.getStart().get(0));
		Assertions.assertEquals("CLOSED", sla.getStop().get(0));
		Assertions.assertEquals(36000000L, sla.getThreshold());

		// Check SLA types
		Assertions.assertEquals(2, sla.getTypes().size());
		Assertions.assertEquals("Bug", sla.getTypes().get(0));
		Assertions.assertEquals("New Feature", sla.getTypes().get(1));

		// Check SLA priorities
		Assertions.assertEquals(2, sla.getPriorities().size());
		Assertions.assertEquals("Blocker", sla.getPriorities().get(0));
		Assertions.assertEquals("Critical", sla.getPriorities().get(1));

		// Check SLA resolutions
		Assertions.assertEquals(2, sla.getResolutions().size());
		Assertions.assertEquals("Fixed", sla.getResolutions().get(0));
		Assertions.assertEquals("Won't Fix", sla.getResolutions().get(1));

		// Check business ranges
		final INamableBean<Integer> calendar = configurationVo.getCalendar();
		Assertions.assertEquals("France", calendar.getName());
		Assertions.assertTrue(calendar.getId() > 0);
		final List<BusinessHours> businessHours = configurationVo.getBusinessHours();
		Assertions.assertEquals(2, businessHours.size());
		Assertions.assertEquals(9 * DateUtils.MILLIS_PER_HOUR, businessHours.get(0).getStart());
		Assertions.assertEquals(12 * DateUtils.MILLIS_PER_HOUR, businessHours.get(0).getEnd());
		Assertions.assertEquals(13 * DateUtils.MILLIS_PER_HOUR, businessHours.get(1).getStart());
		Assertions.assertEquals(18 * DateUtils.MILLIS_PER_HOUR, businessHours.get(1).getEnd());
		Assertions.assertEquals(9 * DateUtils.MILLIS_PER_HOUR, businessHours.get(0).getStart());

		// Check available statuses
		final List<String> statuses = configurationVo.getStatuses();
		Assertions.assertEquals(1, statuses.size());
		Assertions.assertEquals("OPEN", statuses.get(0));
		Assertions.assertEquals(9 * DateUtils.MILLIS_PER_HOUR, businessHours.get(0).getStart());

		// Check available types
		final List<String> types = configurationVo.getTypes();
		Assertions.assertEquals(1, types.size());
		Assertions.assertEquals("Bug", types.get(0));

		// Check available priorities
		final List<String> priorities = configurationVo.getPriorities();
		Assertions.assertEquals(1, priorities.size());
		Assertions.assertEquals("Critical", priorities.get(0));

		// Check available resolutions
		final List<String> resolutions = configurationVo.getResolutions();
		Assertions.assertEquals(1, resolutions.size());
		Assertions.assertEquals("Fixed", resolutions.get(0));

		Assertions.assertEquals("service:bt", resource.getKey());
	}

	@Test
	void deleteUnknown() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> resource.delete(-1, false));
	}

	@Test
	void delete() {
		final Project project = new Project();
		project.setName("TEST");
		project.setPkey("test");
		em.persist(project);

		final Subscription subscription = new Subscription();
		subscription.setProject(project);
		subscription.setNode(nodeRepository.findOneExpected("service:bt"));
		em.persist(subscription);

		final Calendar calendar = new Calendar();
		calendar.setName("Any");
		em.persist(calendar);

		final BugTrackerConfiguration configuration = new BugTrackerConfiguration();
		configuration.setSubscription(subscription);
		configuration.setCalendar(calendar);
		em.persist(configuration);

		final Sla sla = new Sla();
		sla.setName("Any");
		sla.setConfiguration(configuration);
		sla.setStart("Open");
		sla.setStop("Resolved");
		em.persist(sla);

		final BusinessHours businessHours = new BusinessHours();
		businessHours.setConfiguration(configuration);
		em.persist(businessHours);

		Assertions.assertEquals(1, subscriptionRepository.findAllByProject(project.getId()).size());
		em.flush();
		em.clear();

		resource.delete(subscription.getId(), false);
		subscriptionRepository.delete(subscription);
		em.flush();
		em.clear();
		Assertions.assertEquals(0, subscriptionRepository.findAllByProject(project.getId()).size());
	}

	@Test
	void createCreateDefault() {
		slaRepository.deleteAll();
		repository.deleteAll();
		businessHoursRepository.deleteAll();
		holidayRepository.deleteAll();
		calendarRepository.deleteAll();
		em.flush();
		em.clear();

		final Project project = new Project();
		project.setName("TEST");
		project.setPkey("test");
		em.persist(project);
		em.flush();

		final Subscription subscription = new Subscription();
		subscription.setProject(project);
		subscription.setNode(nodeRepository.findOneExpected("service:bt"));
		em.persist(subscription);
		em.flush();
		em.clear();

		resource.create(subscription.getId());
		em.flush();
		em.clear();

		// Check default SLA
		final BugTrackerConfiguration configuration = repository.findBySubscriptionFetch(subscription.getId());
		checkSla(configuration);

		// Check default calendar
		final Calendar calendar = calendarRepository.getDefault();
		Assertions.assertEquals(configuration.getCalendar(), calendar);
		Assertions.assertNotNull(calendar);
		Assertions.assertEquals(4, calendar.getHolidays().size());
		Assertions.assertEquals("Default", calendar.getName());
		Assertions.assertNotNull(calendar.getHolidays().get(0).getName());
		Assertions.assertNotNull(calendar.getHolidays().get(0).getDate());

		// Check default business hours
		Assertions.assertEquals(1, configuration.getBusinessHours().size());
		Assertions.assertEquals(8 * DateUtils.MILLIS_PER_HOUR, configuration.getBusinessHours().get(0).getStart());
		Assertions.assertEquals(18 * DateUtils.MILLIS_PER_HOUR, configuration.getBusinessHours().get(0).getEnd());

		em.flush();
		em.clear();
		Assertions.assertEquals(1, subscriptionRepository.findAllByProject(project.getId()).size());
	}

	private void checkSla(final BugTrackerConfiguration configuration) {
		Assertions.assertNotNull(configuration);
		final List<Sla> slas = slaRepository.findBySubscription(configuration.getSubscription().getId());
		Assertions.assertEquals(1, slas.size());
		final Sla sla = slas.get(0);
		Assertions.assertEquals("Closing", sla.getName());
		Assertions.assertEquals("Closing : Open->Closed", sla.getDescription());
		Assertions.assertEquals(0, sla.getThreshold());
		Assertions.assertEquals("OPEN", sla.getStart());
		Assertions.assertEquals("CLOSED", sla.getStop());
		Assertions.assertNull(sla.getPause());
	}

	@Test
	void create() {
		createOrLink(subscription -> resource.create(subscription.getId()));
	}

	@Test
	void link() {
		createOrLink(subscription -> resource.link(subscription.getId()));
	}

	void createOrLink(final Consumer<Subscription> function) {

		final Project project = new Project();
		project.setName("TEST");
		project.setPkey("test");
		em.persist(project);
		em.flush();

		final Subscription subscription = new Subscription();
		subscription.setProject(project);
		subscription.setNode(nodeRepository.findOneExpected("service:bt"));
		em.persist(subscription);
		em.flush();
		em.clear();

		function.accept(subscription);
		em.flush();
		em.clear();

		// Check SLA
		final BugTrackerConfiguration configuration = repository.findBySubscriptionFetch(subscription.getId());
		checkSla(configuration);

		// Check calendar
		final Calendar calendar = configuration.getCalendar();
		Assertions.assertNotNull(calendar);
		Assertions.assertEquals(66, calendar.getHolidays().size());
		Assertions.assertEquals("France", calendar.getName());
		Assertions.assertEquals("Jour de l'an", calendar.getHolidays().get(0).getName());
		Assertions.assertNotNull(calendar.getHolidays().get(0).getDate());

		// Check default business hours
		Assertions.assertEquals(1, configuration.getBusinessHours().size());
		Assertions.assertEquals(8 * DateUtils.MILLIS_PER_HOUR, configuration.getBusinessHours().get(0).getStart());
		Assertions.assertEquals(18 * DateUtils.MILLIS_PER_HOUR, configuration.getBusinessHours().get(0).getEnd());

		em.flush();
		em.clear();
		Assertions.assertEquals(1, subscriptionRepository.findAllByProject(project.getId()).size());
	}

	@Test
	void addSla() {
		em.flush();
		em.clear();
		final SlaEditionVo vo = new SlaEditionVo();
		vo.setName("AA");
		vo.setDescription("ADescription");
		vo.setStart(identifierHelper.asList("Open"));
		vo.setStop(identifierHelper.asList("Closed"));
		vo.setPause(new ArrayList<>());
		vo.getPause().add("Wait");
		vo.getPause().add("Expect");
		vo.setThreshold(5);
		vo.setSubscription(subscription);
		final int id = resource.addSla(vo);
		Assertions.assertTrue(id > 0);
		em.flush();
		em.clear();
		final Sla sla = slaRepository.findBySubscription(subscription).iterator().next();
		Assertions.assertEquals(sla.getId().intValue(), id);
		Assertions.assertEquals("AA", sla.getName());
		Assertions.assertEquals("ADescription", sla.getDescription());
		Assertions.assertEquals("OPEN", sla.getStart());
		Assertions.assertEquals("CLOSED", sla.getStop());
		Assertions.assertEquals("EXPECT,WAIT", sla.getPause());
		Assertions.assertEquals(5, sla.getThreshold());
	}

	@Test
	void deleteSla() {
		final int id = slaRepository.findBySubscription(subscription).iterator().next().getId();
		em.flush();
		em.clear();
		resource.deleteSla(id);
		em.flush();
		em.clear();
		Assertions.assertEquals(0, slaRepository.findBySubscription(subscription).size());
	}

	@Test
	void addSlaBoundStart() {
		final SlaEditionVo vo = new SlaEditionVo();
		vo.setName("AA");
		vo.setStart(identifierHelper.asList("Open"));
		vo.setStop(identifierHelper.asList("Resolved"));
		vo.setPause(new ArrayList<>());
		vo.getPause().add("Open");
		vo.setSubscription(subscription);
		em.flush();
		em.clear();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.addSla(vo)), "start", "SlaBound");
	}

	@Test
	void addSlaBoundEnd() {
		final SlaEditionVo vo = new SlaEditionVo();
		vo.setName("AA");
		vo.setStart(identifierHelper.asList("Open"));
		vo.setStop(identifierHelper.asList("Resolved"));
		vo.setPause(new ArrayList<>());
		vo.getPause().add("Resolved");
		vo.setSubscription(subscription);
		em.flush();
		em.clear();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.addSla(vo)), "stop", "SlaBound");
	}

	@Test
	void updateSla() {
		final Sla oldEntity = slaRepository.findBySubscription(subscription).get(0);
		final SlaEditionVo vo = new SlaEditionVo();
		vo.setName("AA");
		vo.setDescription("ADescription");
		vo.setStart(identifierHelper.asList("Open"));
		vo.setStop(identifierHelper.asList("Resolved"));
		vo.setPause(new ArrayList<>());
		vo.getPause().add("One");
		vo.getPause().add("Any");
		vo.setThreshold(5);
		vo.setSubscription(subscription);
		vo.setId(oldEntity.getId());
		em.flush();
		em.clear();
		resource.updateSla(vo);
		em.flush();
		em.clear();
		final Sla sla = slaRepository.findBySubscription(subscription).iterator().next();
		Assertions.assertEquals(sla.getId(), vo.getId());
		Assertions.assertEquals("AA", sla.getName());
		Assertions.assertEquals("ADescription", sla.getDescription());
		Assertions.assertEquals("OPEN", sla.getStart());
		Assertions.assertEquals("RESOLVED", sla.getStop());
		Assertions.assertEquals("ANY,ONE", sla.getPause());
		Assertions.assertEquals(5, sla.getThreshold());
	}

	@Test
	void addBusinessHours() {
		final BusinessHoursEditionVo vo = new BusinessHoursEditionVo();
		vo.setStart(1);
		vo.setEnd(2);
		vo.setSubscription(subscription);
		em.flush();
		em.clear();
		final int id = resource.addBusinessHours(vo);
		Assertions.assertTrue(id > 0);
		em.flush();
		em.clear();
		final BusinessHours entity = repository.findBySubscription(subscription).getBusinessHours().iterator().next();
		Assertions.assertEquals(entity.getId().intValue(), id);
		Assertions.assertEquals(1, entity.getStart());
		Assertions.assertEquals(2, entity.getEnd());
	}

	@Test
	void updateBusinessHours() {
		final BusinessHours oldEntity = repository.findBySubscription(subscription).getBusinessHours().iterator().next();
		final BusinessHoursEditionVo vo = new BusinessHoursEditionVo();
		vo.setStart(1);
		vo.setEnd(2);
		vo.setSubscription(subscription);
		vo.setId(oldEntity.getId());
		em.flush();
		em.clear();
		resource.updateBusinessHours(vo);
		em.flush();
		em.clear();
		final BusinessHours entity = repository.findBySubscription(subscription).getBusinessHours().iterator().next();
		Assertions.assertEquals(entity.getId(), vo.getId());
		Assertions.assertEquals(1, entity.getStart());
		Assertions.assertEquals(2, entity.getEnd());
	}

	@Test
	void addBusinessHoursOverlapsStart() {
		final BusinessHoursEditionVo vo = new BusinessHoursEditionVo();
		vo.setStart(10 * DateUtils.MILLIS_PER_HOUR);
		vo.setEnd(23 * DateUtils.MILLIS_PER_HOUR);
		vo.setSubscription(subscription);
		em.flush();
		em.clear();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.addBusinessHours(vo)), "start", "Overlap");
	}

	@Test
	void addBusinessHoursOverlapsEnd() {
		final BusinessHoursEditionVo vo = new BusinessHoursEditionVo();
		vo.setStart(2 * DateUtils.MILLIS_PER_HOUR);
		vo.setEnd(DateUtils.MILLIS_PER_HOUR);
		vo.setSubscription(subscription);
		em.flush();
		em.clear();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.addBusinessHours(vo)), "stop", "Overlap");
	}

	@Test
	void deleteBusinessHours() {
		Assertions.assertEquals(2, repository.findBySubscription(subscription).getBusinessHours().size());
		final int id = repository.findBySubscription(subscription).getBusinessHours().iterator().next().getId();
		em.flush();
		em.clear();
		resource.deleteBusinessHours(id);
		em.flush();
		em.clear();
		Assertions.assertEquals(1, repository.findBySubscription(subscription).getBusinessHours().size());
	}

	@Test
	void deleteLastBusinessHours() {
		Assertions.assertEquals(2, repository.findBySubscription(subscription).getBusinessHours().size());
		final int id0 = repository.findBySubscription(subscription).getBusinessHours().iterator().next().getId();
		em.flush();
		em.clear();
		resource.deleteBusinessHours(id0);
		em.flush();
		em.clear();
		Assertions.assertEquals(1, repository.findBySubscription(subscription).getBusinessHours().size());
		em.flush();
		em.clear();

		// Try to delete the last one
		final int id = repository.findBySubscription(subscription).getBusinessHours().iterator().next().getId();
		Assertions.assertEquals("service:bt:no-business-hours", Assertions.assertThrows(BusinessException.class,
				() -> resource.deleteBusinessHours(id)).getMessage());
	}

	@Test
	void setCalendar() {
		final Calendar calendar = new Calendar();
		calendar.setName("Any");
		calendarRepository.saveAndFlush(calendar);
		final int id = calendar.getId();
		em.clear();

		resource.setCalendar(subscription, id);
		em.flush();
		em.clear();
		Assertions.assertEquals(id, repository.findBySubscription(subscription).getCalendar().getId().intValue());

	}

	@Test
	void getCalendars() {
		final Calendar calendar2 = new Calendar();
		calendar2.setName("Any2");
		calendarRepository.saveAndFlush(calendar2);
		final Calendar calendar1 = new Calendar();
		calendar1.setName("Any1");
		calendarRepository.saveAndFlush(calendar1);
		em.flush();
		em.clear();

		Assertions.assertEquals(3, resource.getAllCalendars().size());
		Assertions.assertEquals("Any1", resource.getAllCalendars().iterator().next().getName());

	}

	@Test
	void getInstalledEntities() {
		Assertions.assertTrue(resource.getInstalledEntities().contains(Holiday.class));
		Assertions.assertTrue(resource.getInstalledEntities().contains(Calendar.class));
	}
}
