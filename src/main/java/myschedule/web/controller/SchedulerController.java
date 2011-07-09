package myschedule.web.controller;

import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

import javax.servlet.http.HttpSession;

import myschedule.service.ErrorCode;
import myschedule.service.ErrorCodeException;
import myschedule.service.Utils;
import myschedule.service.SchedulerServiceContainer;
import myschedule.service.SchedulerServiceDao;
import myschedule.service.Utils.Getter;
import myschedule.service.SchedulerService;
import myschedule.service.SchedulerServiceFinder;

import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/** 
 * Scheduler controller.
 *
 * @author Zemian Deng
 */
@Controller
@RequestMapping(value="/scheduler")
public class SchedulerController {
	
	protected Logger logger = LoggerFactory.getLogger(getClass());
	
	@Autowired @Qualifier("schedulerServiceFinder")
	protected SchedulerServiceFinder schedulerServiceFinder;
	
	@Autowired @Qualifier("schedulerServiceContainer")
	protected SchedulerServiceContainer schedulerServiceContainer;

	@Autowired @Qualifier("schedulerServiceFileDao")
	protected SchedulerServiceDao schedulerServiceDao;
	
	@RequestMapping(value="/modify", method=RequestMethod.GET)
	public DataModelMap modify(
			@RequestParam String name, 
			HttpSession session) {
		SchedulerService schedulerService = schedulerServiceContainer.getSchedulerService(name);
		DataModelMap data = new DataModelMap();
		data.addData("schedulerName", schedulerService.getName());
		data.addData("isStarted", schedulerService.isStarted());
		data.addData("isPaused", schedulerService.isPaused());
		data.addData("isStandby", schedulerService.isStandby());
		data.addData("isShutdown", schedulerService.isShutdown());
		
		Properties configProps = schedulerService.getConfigProps();
		if (configProps == null || !schedulerService.isConfigModifiable())
			throw new ErrorCodeException(ErrorCode.WEB_UI_PROBLEM, "ScheduleService " + name + " config props is not editiable.");
		String configPropsText = null;
		String schedulerServiceName = schedulerService.getName();
		if (schedulerServiceDao.hasSchedulerService(schedulerServiceName)) {
			configPropsText = schedulerServiceDao.getConfigPropsText(schedulerServiceName);
		} else {
			configPropsText = Utils.propsToText(configProps);
		}
		
		data.addData("configPropsText", configPropsText);
		data.addData("schedulerServiceName", schedulerServiceName);
		return data; 
	}
	
	@RequestMapping(value="/modify-action", method=RequestMethod.POST)
	public DataModelMap modifyAction(
			@RequestParam String schedulerServiceName,
			@RequestParam String configPropsText,
			HttpSession session) {
		// Parse form config into props
		Properties configProps = Utils.loadPropertiesFromText(configPropsText);
		
		// Create a new SchedulerService.
		SchedulerService schedulerService = schedulerServiceContainer.getSchedulerService(schedulerServiceName);
		boolean origRunning = schedulerService.isStarted() && !schedulerService.isPaused();
		
		// Bring the scheduler down and update the config
		schedulerService.shutdown();		
		schedulerService.setConfigProps(configProps);
		
		// Re-init it now.
		schedulerService.init();
		logger.info("SchedulerService " + schedulerServiceName + " has been updated and re-initialized.");
		
		// Update storage config
		schedulerServiceDao.saveSchedulerService(schedulerService, configPropsText, true);
		logger.info("SchedulerService " + schedulerService.getName() + " configProps has been updated.");
		
		if (!schedulerService.isStarted() && origRunning) {
			schedulerService.start();
			logger.info("SchedulerService " + schedulerServiceName + " has been re-started.");
		}
		DataModelMap data = new DataModelMap();
		data.addData("schedulerName", schedulerService.getName());
		data.addData("isStarted", schedulerService.isStarted());
		data.addData("isPaused", schedulerService.isPaused());
		data.addData("isStandby", schedulerService.isStandby());
		data.addData("isShutdown", schedulerService.isShutdown());
		
		data.addData("schedulerService", schedulerService);
		data.addData("origRunning", origRunning);
		
		return data;
	}
	
	@RequestMapping(value="/summary", method=RequestMethod.GET)
	public DataModelMap summary(HttpSession session) {
		SchedulerService schedulerService = schedulerServiceFinder.find(session);
		DataModelMap data = new DataModelMap();
		data.addData("schedulerName", schedulerService.getName());
		data.addData("isStarted", schedulerService.isStarted());
		data.addData("isPaused", schedulerService.isPaused());
		data.addData("isStandby", schedulerService.isStandby());
		data.addData("isShutdown", schedulerService.isShutdown());
		try {
		String summary = schedulerService.getSchedulerMetaData().getSummary();
		data.addData("schedulerSummary", summary);
		} catch (SchedulerException e) {
			throw new ErrorCodeException(ErrorCode.WEB_UI_PROBLEM, "Unable to get scheduler summary info.", e);
		}
		return data;
	}

	@RequestMapping(value="/detail", method=RequestMethod.GET)
	public DataModelMap detail(HttpSession session) {
		SchedulerService schedulerService = schedulerServiceFinder.find(session);
		DataModelMap data = new DataModelMap();
		data.addData("schedulerName", schedulerService.getName());
		data.addData("isStarted", schedulerService.isStarted());
		data.addData("isPaused", schedulerService.isPaused());
		data.addData("isStandby", schedulerService.isStandby());
		data.addData("isShutdown", schedulerService.isShutdown());
		if (schedulerService.isStarted() && !schedulerService.isShutdown()) {
			data.addData("jobCount", "" + schedulerService.getJobDetails().size());
			SchedulerMetaData schedulerMetaData = schedulerService.getSchedulerMetaData();
			data.addData("schedulerDetailMap", getSchedulerDetail(schedulerMetaData));
		}
		return data;
	}

	@RequestMapping(value="/pause", method=RequestMethod.GET)
	public String pause(HttpSession session) {
		SchedulerService schedulerService = schedulerServiceFinder.find(session);
		schedulerService.pause();
		return "redirect:detail";
	}
	
	@RequestMapping(value="/resume", method=RequestMethod.GET)
	public String resume(HttpSession session) {
		SchedulerService schedulerService = schedulerServiceFinder.find(session);
		schedulerService.resume();
		return "redirect:detail";
	}
	
	@RequestMapping(value="/start", method=RequestMethod.GET)
	public String start(HttpSession session) {
		SchedulerService schedulerService = schedulerServiceFinder.find(session);
		schedulerService.start();
		return "redirect:detail";
	}
	
	@RequestMapping(value="/standby", method=RequestMethod.GET)
	public String standby(HttpSession session) {
		SchedulerService schedulerService = schedulerServiceFinder.find(session);
		schedulerService.standby();
		return "redirect:detail";
	}
	
	protected TreeMap<String, String> getSchedulerDetail(SchedulerMetaData schedulerMetaData) {
		TreeMap<String, String> schedulerInfo = new TreeMap<String, String>();
		List<Getter> getters = Utils.getGetters(schedulerMetaData);
		for (Getter getter : getters) {
			String key = getter.getPropName();
			if (key.length() >= 1) {
				if (key.equals("summary")) // skip this field.
					continue;
				key = key.substring(0, 1).toUpperCase() + key.substring(1);
				String value = Utils.getGetterStrValue(getter);
				schedulerInfo.put(key, value);
			} else {
				logger.warn("Skipped a scheduler info key length less than 1 char. Key=" + key);
			}
		}
		logger.debug("Scheduler info map has " + schedulerInfo.size() + " items.");
		return schedulerInfo;
	}
}
