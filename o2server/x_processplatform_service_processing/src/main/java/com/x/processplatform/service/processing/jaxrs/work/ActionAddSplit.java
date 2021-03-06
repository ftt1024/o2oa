package com.x.processplatform.service.processing.jaxrs.work;

import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonElement;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.container.factory.EntityManagerContainerFactory;
import com.x.base.core.entity.annotation.CheckPersistType;
import com.x.base.core.project.annotation.FieldDescribe;
import com.x.base.core.project.exception.ExceptionEntityNotExist;
import com.x.base.core.project.http.ActionResult;
import com.x.base.core.project.http.EffectivePerson;
import com.x.base.core.project.jaxrs.WoId;
import com.x.base.core.project.tools.StringTools;
import com.x.processplatform.core.entity.content.Work;
import com.x.processplatform.core.entity.content.WorkLog;
import com.x.processplatform.service.processing.Business;
import com.x.processplatform.service.processing.Processing;
import com.x.processplatform.service.processing.ProcessingAttributes;

class ActionAddSplit extends BaseAction {

	ActionResult<Wo> execute(EffectivePerson effectivePerson, String id, JsonElement jsonElement) throws Exception {

		try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
			ActionResult<Wo> result = new ActionResult<>();
			Business business = new Business(emc);
			Wi wi = this.convertToWrapIn(jsonElement, Wi.class);
			/* 校验work是否存在 */
			Work work = emc.find(id, Work.class);
			if (null == work) {
				throw new ExceptionEntityNotExist(id, Work.class);
			}
			if (!work.getSplitting()) {
				throw new ExceptionNotSplit(work.getId());
			}
			if (StringUtils.isEmpty(wi.getSplitValue())) {
				throw new ExceptionEmptySplitValue(work.getId());
			}

			WorkLog arrived = this.getWorkLogArrived(business, work);

			if (null == arrived) {
				throw new ExceptionInvalidArrivedWorkLog(work.getActivityToken());
			}

			WorkLog from = this.getWorkLogFrom(business, work);

			if (null == from) {
				throw new ExceptionInvalidFromWorkLog(work.getActivityToken());
			}

			emc.beginTransaction(Work.class);
			emc.beginTransaction(WorkLog.class);

			Work workCopy = new Work(work);

			WorkLog arrivedCopy = new WorkLog(arrived);

			WorkLog fromCopy = new WorkLog(from);

			/* 设置work */
			arrivedCopy.setWork(workCopy.getId());
			fromCopy.setWork(workCopy.getId());

			workCopy.setSplitValue(wi.getSplitValue());
			arrivedCopy.setSplitValue(wi.getSplitValue());
			fromCopy.setSplitValue(wi.getSplitValue());
			/* 重置到达值 */
			String activityToken = StringTools.uniqueToken();
			workCopy.setActivityToken(activityToken);
			arrivedCopy.setArrivedActivityToken(activityToken);
			fromCopy.setFromActivityToken(activityToken);

			/* 清空处理人会导致重新计算当前环节处理人 */
			workCopy.getManualTaskIdentityList().clear();
			emc.persist(workCopy, CheckPersistType.all);
			emc.persist(arrivedCopy, CheckPersistType.all);
			emc.persist(fromCopy, CheckPersistType.all);
			emc.commit();
			Processing processing = new Processing(wi);
			processing.processing(workCopy.getId());
			Wo wo = new Wo();
			wo.setId(workCopy.getId());
			result.setData(wo);
			return result;
		}
	}

	private WorkLog getWorkLogArrived(Business business, Work work) throws Exception {
		return business.entityManagerContainer()
				.listEqual(WorkLog.class, WorkLog.arrivedActivityToken_FIELDNAME, work.getActivityToken()).stream()
				.findFirst().orElse(null);
	}

	private WorkLog getWorkLogFrom(Business business, Work work) throws Exception {
		return business.entityManagerContainer()
				.listEqual(WorkLog.class, WorkLog.fromActivityToken_FIELDNAME, work.getActivityToken()).stream()
				.findFirst().orElse(null);
	}

	public static class Wi extends ProcessingAttributes {

		@FieldDescribe("增加的拆分值")
		private String splitValue;

		public String getSplitValue() {
			return splitValue;
		}

		public void setSplitValue(String splitValue) {
			this.splitValue = splitValue;
		}

	}

	public static class Wo extends WoId {
	}

}