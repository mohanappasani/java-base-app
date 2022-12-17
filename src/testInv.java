package in.rl.bizlog.misreport.service.impl;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import in.rl.bizlog.misreport.dao.InventoryTrxStoresRepository;
import in.rl.bizlog.misreport.dao.MisReportBlockRepository;
import in.rl.bizlog.misreport.dao.MisReportHistoryLogsRepository;
import in.rl.bizlog.misreport.dao.MisReportHistoryRepository;
import in.rl.bizlog.misreport.dao.MisReportProductRepository;
import in.rl.bizlog.misreport.dao.MisReportTicketsRepository;
import in.rl.bizlog.misreport.dao.TicketProcessBarcodesBlockDao;
import in.rl.bizlog.misreport.dao.TicketProcessBarcodesTicketDao;
import in.rl.bizlog.misreport.dao.TicketProcessBlocksDao;
import in.rl.bizlog.misreport.dao.TicketProcessProductsDao;
import in.rl.bizlog.misreport.dao.TicketProcessRemarksDao;
import in.rl.bizlog.misreport.service.BaseService;
import in.rl.bizlog.misreport.service.MisServices;
import in.rl.bizlog.misreport.service.SmsIntegrationToClients;
import in.rl.bizlog.roots.model.core.mis.InventoryTrxStores;
import in.rl.bizlog.roots.model.core.mis.MisReportHistory;
import in.rl.bizlog.roots.model.core.mis.MisReportHistoryLogs;
import in.rl.bizlog.roots.model.core.tickets.TicketProcessBarcodesBlock;
import in.rl.bizlog.roots.model.core.tickets.TicketProcessBarcodesTicket;
import in.rl.bizlog.roots.model.core.tickets.TicketProcessBlocks;
import in.rl.bizlog.roots.model.core.tickets.TicketProcessProducts;
import in.rl.bizlog.roots.model.core.tickets.TicketProcessRemarks;

import in.rl.bizlog.roots.util.IdGenerate;
import in.rl.bizlog.roots.util.Response;
import in.rl.bizlog.roots.util.ResponseMessage;

@Service
public class MisReportImpl extends BaseService implements MisServices {

	@Autowired
	private MisReportTicketsRepository misTicketRepo;

	@Autowired
	private MisReportBlockRepository misBlockRepo;

	@Autowired
	private MisReportHistoryRepository misHistoryRepo;

	@Autowired
	private MisReportHistoryLogsRepository misHistoryLogsRepo;

	@Autowired
	private MisReportTicketsRepository misReportTicketsRepository;

	@Autowired
	private MisReportBlockRepository misReportBlockRepository;

	@Autowired
	private MisReportProductRepository misReportProdRepository;

	@Autowired
	private TicketProcessBarcodesBlockDao ticketProcessBarcodesBlock;

	@Autowired
	private TicketProcessBarcodesTicketDao ticketProcessBarcodesTicket;

	@Autowired
	private TicketProcessRemarks ticketProcessRemarks;

	@Autowired
	private TicketProcessProducts ticketProcessProducts;

	@Autowired
	private TicketProcessRemarksDao ticketProcessRemarksDao;

	@Autowired
	private TicketProcessProductsDao ticketProcessProductsDao;

	@Autowired
	private TicketProcessBlocksDao ticketProcessBlocksDao;

	@Autowired
	private Response response;

	@Autowired
	private MisReportServices misReportServices;

	@Autowired
	private RetailerMISConfigs retailerMIS;

	@Autowired
	private SmsIntegrationToClients smsIntegrationToClients;

	@Autowired
	private InventoryTrxStoresRepository inventoryTrxStoresRepository;

//	@Autowired
//	private MisReportHistory misReportHistory;

	@SuppressWarnings("unchecked")
	@Override
	public Response insertUpdateMisReport(String reqJson) {

		Map<String, Object> requestMap = new Gson().fromJson(reqJson, Map.class);

		if (!requestMap.containsKey("ticketProductId")) {
			requestMap.put("ticketProductId", "");
		}
		if (!requestMap.containsKey("productId")) {
			requestMap.put("productId", "");
		}
		if (!requestMap.containsKey("ticketBlockId")) {
			requestMap.put("ticketBlockId", "");
		}

		try {

//			Map<String, Object> smsResponse = 
			smsIntegrationToClients.smsIntegration(requestMap); /* from this line it will send sms to client */
			this.checkReportTicket(String.valueOf(requestMap.get("processId")));
			MisReportHistoryLogs historyReportLogs = null;

			/**
			 * Write to History Log
			 */
			try {
				historyReportLogs = this.misHistoryReportLogs(requestMap, "", "");
			} catch (Exception e) {

				e.printStackTrace();
			}

			/**
			 * Validate if actionstatus is present
			 */
			if (!validateInput(requestMap).equals("success")) {
				this.updateHistoryLogs(historyReportLogs, "", "ErrorNoActionCode");
				response.respFail(requestMap, validateInput(requestMap));
				return response;
			}

			Map<String, Object> resp = new HashMap<String, Object>();

			/**
			 * Check if current procssedAt > last processedAt in misHistoryReport table
			 **/
			if (!validateProcessdAt(requestMap).equals("success")) {
				System.out.println("=========>4<==============");
				this.updateHistoryLogs(historyReportLogs, "", "processedAt is old");
				resp = updateResponseIfBlockExists(requestMap);
				// resp.put("ticketNo", "value");
				response.resp200(resp, "Successfully Updated  Mis report");
				return response;
			}

			/**
			 * Process the Request
			 */
			resp = updateBlockReport(requestMap);
//			resp.put("smsResponse", smsResponse);

			/**
			 * Push Ticket Scans
			 */
			Map<String, String> actioninfo = (Map<String, String>) resp.get("actionInfo");
			try {
				if (!String.valueOf(actioninfo.get("blockStatus")).equals("")) {
					updateStatus2Retailers(requestMap, String.valueOf(actioninfo.get("blockStatus")));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			/**
			 * Update History Log Save to History with Details
			 */
			try {

				this.updateHistoryLogs(historyReportLogs, String.valueOf(actioninfo.get("preStatus")),
						String.valueOf(actioninfo.get("blockStatus")));

			} catch (Exception e) {
				e.printStackTrace();
			}

			if (String.valueOf(resp.get("methodStatus")).equals("success")) {
				this.misHistoryReport(requestMap, String.valueOf(actioninfo.get("preStatus")),
						String.valueOf(actioninfo.get("blockStatus")));
				response.resp200(resp, "Successfully Updated  Mis report");
			} else {
//				this.misHistoryReport(requestMap, String.valueOf(actioninfo.get("preStatus")),
//						"Failed::" + String.valueOf(actioninfo.get("blockStatus")));

				this.misHistoryReportLogs(requestMap, String.valueOf(actioninfo.get("preStatus")),
						"Failed::" + String.valueOf(actioninfo.get("blockStatus")));
				resp.put("payload", requestMap);
				this.logAPIError(resp);
				response.resp400(resp, String.valueOf(resp.get("methodMsg")));
			}

		} catch (Exception e) {
			try {
				this.logAPIError(requestMap);
			} catch (Exception e2) {
				e2.printStackTrace();
			}

			e.printStackTrace();
			response.respFail(null, BaseService.getRootException(e).getMessage());
		}
		return response;
	}

	private void updateHistoryLogs(MisReportHistoryLogs historyReportLogs, String preStatus, String blockStatus) {

		System.out.println(blockStatus + "<=============================");
		try {
			if (!historyReportLogs.equals(null)) {
				historyReportLogs.setPreStatus(preStatus);
				historyReportLogs.setBlockStatus(blockStatus);
				misHistoryLogsRepo.save(historyReportLogs);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private String validateInput(Map<String, Object> requestMap) {

		Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

		String actionStatus = String.valueOf(resultSet.get("actionStatus"));

		if (actionStatus.equals("") || actionStatus.equals(null)) {
			return "Validation Error:Action Status cannot be Empty or Null";
		}

		return "success";

	}

	@SuppressWarnings("unchecked")
	@Override
	public Response insertUpdateMisReportRetailer(String reqJson) throws JSONException {

		Map<String, Object> requestMap = new Gson().fromJson(reqJson, Map.class);
		Map<String, Object> resp = this.updateStatus2Retailers(requestMap, "");
		response.resp200(resp, "testing MIS");
		return response;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> updateStatus2Retailers(Map<String, Object> requestMap, String blockStatus)
			throws JSONException {

		Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
		String actionStatus = String.valueOf(resultSet.get("actionStatus"));

		String actionCode = String.valueOf(requestMap.get("actionCode"));

		if (blockStatus.equals("")) {
			blockStatus = actionStatus;
		}

		Map<String, Object> resp = new LinkedHashMap<>();

		// Get retailerCode & ticketNo by ticketProcessId
		String retailerCode = "";
		String ticketNo = "";
		String processId = String.valueOf(requestMap.get("processId"));

		List<Map<String, String>> ticketDetails = misReportBlockRepository.getTicketNoRetailerCode(processId);

		/* AMTRUST ANOTHER API CONFIGURATION */
		String code = ticketDetails.get(0).get("CODE");
		String ticketScanUrl = "1";
		if (code.equals("AMS")) {
			System.out.println("PRIMARY_DETAILS" + ticketDetails.get(0).get("PRIMARY_DETAILS"));

			JSONObject scans = new JSONObject(ticketDetails.get(0).get("PRIMARY_DETAILS"));

			try {
				ticketScanUrl = scans.getString("ticketScanUrl");
				System.out.println("ticketScanUrl-->" + ticketScanUrl);
			} catch (Exception e) {
				System.out.println("ticketScanUrl-->" + ticketScanUrl);
				e.printStackTrace();

			}
		}

		/* END */

		Map<String, String> ticketRow;
		if (ticketDetails.size() > 0) {

			ticketRow = ticketDetails.get(0);
			ticketNo = ticketDetails.get(0).get("TICKET_NO");
			retailerCode = ticketDetails.get(0).get("CODE");
		} else {

			resp.put("result", "ticketNotFound");
			return resp;
		}

		Map<String, Object> updateStatus = retailerMIS.getMisStatus(requestMap, ticketRow, retailerCode, blockStatus);
		resp.put("rtlStatus", updateStatus);

		// isStatusUpdateRequired
		if (String.valueOf(updateStatus.get("blockStatus")).equals("NOT_REQUIRED")) {

			resp.put("result", "notRequired");
			return resp;
		}

		/* AMTRUST ANOTHER API END POINT */

		if (ticketScanUrl.equals("2")) {
			System.out.println("NEW AMTRUST API END POINT");
			retailerCode = "AMS_ALT";
		}

		/* END */
		
		/* ONE ASSIST BUYBACK */
		if (retailerCode.equals("OA")) {
			if (String.valueOf(ticketRow.get("FLOW_ID")).equals("BuyBack")) {
				retailerCode = "OA_BUYBACK";
			}
		}
		
		/* END */

		Map<String, String> retailerUrl = retailerMIS.getRetailerURL(retailerCode);
		resp.put("url", retailerUrl.get("url"));

//		JSONObject reqResp = retailerMIS.getReqJson(ticketRow, retailerCode, ticketNo, updateStatus);

//		resp.put("param", new Gson().fromJson(String.valueOf(reqResp), HashMap.class));
//

//		retailerMIS.send2Retailer(retailerUrl, retailerCode, reqResp);

		/*
		 * OLD CODE
		 * 
		 * <<<<<<< HEAD // if (retailerCode.equals("TCQ")) { // JSONObject reqResp =
		 * retailerMIS.getReqJsonTCQ(ticketRow, retailerCode, ticketNo, updateStatus,
		 * requestMap); // System.out.println("==============>BASEAUTH===============" +
		 * retailerUrl.get("baseAuth")); // // resp.put("param", new
		 * Gson().fromJson(String.valueOf(reqResp), HashMap.class)); // //
		 * System.out.println("==============>Before Method Call<==============="); //
		 * retailerMIS.send2Retailer(retailerUrl, retailerCode, reqResp); // } else {
		 * //// JSONObject reqResp = retailerMIS.getReqJson(ticketRow, retailerCode,
		 * ticketNo, updateStatus); // JSONObject reqResp =
		 * retailerMIS.getReqJson(ticketRow, retailerCode, ticketNo,
		 * updateStatus,requestMap); //
		 * System.out.println("==============>ok==============="); // //
		 * resp.put("param", new Gson().fromJson(String.valueOf(reqResp),
		 * HashMap.class)); // //
		 * System.out.println("==============>Before Method Call<==============="); //
		 * retailerMIS.send2Retailer(retailerUrl, retailerCode, reqResp); // }
		 * 
		 * 
		 */

//			JSONObject reqResp = retailerMIS.getReqJson(ticketRow, retailerCode, ticketNo, updateStatus);
		JSONObject reqResp = retailerMIS.getReqJson(ticketRow, retailerCode, ticketNo, updateStatus, requestMap);
		System.out.println("==============>ok===============");
		resp.put("param", new Gson().fromJson(String.valueOf(reqResp), HashMap.class));
		retailerMIS.send2Retailer(retailerUrl, retailerCode, reqResp);

		// For RCD Additional
		if (actionCode.equals("wFeAssign") && retailerCode.equals("RCD")) {
			JSONObject rcdReqJSON = retailerMIS.getReqJsonRCDFeAssign(ticketRow, retailerCode, ticketNo, updateStatus,
					requestMap);
			retailerMIS.send2Retailer(retailerUrl, retailerCode, rcdReqJSON);
		}

		// For SER Additional
		if (String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE") && retailerCode.equals("SER")) {
			JSONObject rcdReqJSON = retailerMIS.getReqJsonSERRescheduleWeb(ticketRow, retailerCode, ticketNo,
					updateStatus, requestMap);
			retailerMIS.send2Retailer(retailerUrl, retailerCode, rcdReqJSON);
		} else if (String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE_FE_AT_PICKUP")
				&& retailerCode.equals("SER")
				|| String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE_FE_AT_DROP")
						&& retailerCode.equals("SER")) {
			JSONObject rcdReqJSON = retailerMIS.getReqJsonSERReschedule(ticketRow, retailerCode, ticketNo, updateStatus,
					requestMap);
			retailerMIS.send2Retailer(retailerUrl, retailerCode, rcdReqJSON);
		}

		// For SPD Additional
		if (String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE") && retailerCode.equals("SPD")) {
			JSONObject rcdReqJSON = retailerMIS.getReqJsonSPDRescheduleWeb(ticketRow, retailerCode, ticketNo,
					updateStatus, requestMap);
			retailerMIS.send2Retailer(retailerUrl, retailerCode, rcdReqJSON);
		} else if (String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE_FE_AT_PICKUP")
				&& retailerCode.equals("SPD")
				|| String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE_FE_AT_DROP")
						&& retailerCode.equals("SPD")) {
			JSONObject rcdReqJSON = retailerMIS.getReqJsonSPDReschedule(ticketRow, retailerCode, ticketNo, updateStatus,
					requestMap);
			retailerMIS.send2Retailer(retailerUrl, retailerCode, rcdReqJSON);
		}

		// For LFL Additional
		if (String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE") && retailerCode.equals("LFL")
				|| String.valueOf(resultSet.get("actionStatus")).equals("AVAILABLE") && retailerCode.equals("LFL")) {
			JSONObject lflReqJSON = retailerMIS.getReqJsonLFLRescheduleAndAvailableWeb(ticketRow, retailerCode,
					ticketNo, updateStatus, requestMap);
			retailerMIS.send2Retailer(retailerUrl, retailerCode, lflReqJSON);
		} else if (String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE_FE_AT_PICKUP")
				&& retailerCode.equals("LFL")
				|| String.valueOf(resultSet.get("actionStatus")).equals("RESCHEDULE_FE_AT_DROP")
						&& retailerCode.equals("LFL")) {
			JSONObject lflReqJSON = retailerMIS.getReqJsonLFLReschedule(ticketRow, retailerCode, ticketNo, updateStatus,
					requestMap);
			retailerMIS.send2Retailer(retailerUrl, retailerCode, lflReqJSON);
		}

		return resp;

	}

//	@SuppressWarnings("unchecked")
//	private void wAptmCusAvailable(Map<String, Object> requestMap, String ticketBlockId) {
//
//		Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
//		String actionStatus = String.valueOf(resultSet.get("actionStatus"));
//		String blockStatus = actionStatus;
//
//		switch (actionStatus) {
//		case "AVAILABLE":
//			blockStatus = "CHOOSE_APPOINTMENT_DATE";
//			break;
//		case "RESCHEDULE":
//			blockStatus = "CHOOSE_RE_APPOINTMENT_DATE";
//			break;
//		case "REJECT_SERVICE":
//			blockStatus = "CANCEL";
//			break;
//		default:
//			blockStatus = actionStatus;
//		}

//		 MIS
//		 misReportBlockRepository.updateBlockMISBlockStatus(isAvailable,ticketBlockId);
//		 misReportBlockRepository.updateBlockMISActionStatus(isAvailable,ticketBlockId);
//		misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId);
//
//		// Ops
//		misReportBlockRepository.updateBlockOpsBlockStatus(blockStatus, ticketBlockId);
//		misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
//		// misReportBlockRepository.updateBlockOpsBlockActionStatus(isAvailable,isAvailable,ticketBlockId);
//

//
//	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wAptmFixing(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Integer count;

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String assignToHo = String.valueOf(resultSet.get("assignToHo"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));

			String flowStatus = String.valueOf(requestMap.get("flowStatus"));
			if (flowStatus.equals("") || flowStatus.equals(null)) {
				flowStatus = "PICK_WEB";
			}

			switch (actionStatus) {
			case "AVAILABLE":
				blockStatus = "APPOINTMENT_FIXED";

				break;
			case "RESCHEDULE":
				blockStatus = "IDLE_UNTIL_" + String.valueOf(resultSet.get("appointmentDate"));
				break;
			case "REJECT_SERVICE":
				blockStatus = "PROBLEM_SOLVED_DURING_INITIAL_CALL";
				break;
			case "INVALID_NO":
				this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: Invalid Customer Number");
				blockStatus = "ASSIGN_TO_HO";
				break;
			case "RINGING_NOT_PICKING":
				blockStatus = "IDLE_FOR_15MIN";
				break;
			case "CALL_BACK_LATER":
				blockStatus = "IDLE_FOR_15MIN";
				break;
			default:
				blockStatus = actionStatus;
			}

//		// MIS
//		misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
//				getDateAndTime());
//
//		misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
//		misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

			// If Assign to HO then update Hub ID
			if (assignToHo.equals("yes") && !actionStatus.equals("AVAILABLE")) {

				misReportBlockRepository.updateBlockMISHub(confHoHubId(), ticketBlockId);
				misReportBlockRepository.updateBlockOpsHub(confHoHubId(), ticketBlockId);
			}

			if (actionStatus.equals("AVAILABLE") || actionStatus.equals("RESCHEDULE")) {
				// WAptmFixing
				misReportBlockRepository.updateBlockMISWAptmFixing(String.valueOf(resultSet.get("appointmentDate")),
						ticketBlockId);
				misReportBlockRepository.updateOpsWAptmFixing(String.valueOf(resultSet.get("appointmentDate")),
						ticketBlockId);
			}
			if (!actionStatus.equals("AVAILABLE")) {
				count = misReportBlockRepository.getCount(ticketBlockId);
				count = count + 1;
//				String remark = String.valueOf(resultSet.get("remarks"));

				String remark = String.valueOf(resultSet.get("appointmentDate")) + " | "
						+ String.valueOf(resultSet.get("remarks"));
//				String dateTime = String.valueOf(resultSet.get("appointmentDate"));
				switch (count) {

				case 1:
					misReportBlockRepository.updateAttempt1(ticketBlockId, remark, processAt, count);
					break;
				case 2:
					misReportBlockRepository.updateAttempt2(ticketBlockId, remark, processAt, count);
					break;
				case 3:
					misReportBlockRepository.updateAttempt3(ticketBlockId, remark, processAt, count);
					break;
				case 4:
					misReportBlockRepository.updateAttempt4(ticketBlockId, remark, processAt, count);
					break;
				default:
					misReportBlockRepository.updateAttempt5(ticketBlockId, remark, processAt, count);
					break;
				}

//				misReportBlockRepository.updateCount(ticketBlockId, count);
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wFeAssign(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowId = String.valueOf(resultSet.get("flowId"));
			String flowStatus = "PICK_MOB";

			switch (actionStatus) {
			case "ASSIGNED_FE_FOR_PICKUP":
				blockStatus = "ASSIGNED_FE_FOR_PICKUP";
				break;
			case "ASSIGNED_FE_FOR_SERVICE_PICKUP":
				blockStatus = "ASSIGNED_FE_FOR_SERVICE_PICKUP";
				break;
			case "ASSIGNED_FE_FOR_SERVICE_DROP":
				blockStatus = "ASSIGNED_FE_FOR_SERVICE_DROP";
				break;
			case "ASSIGNED_FE_FOR_DROP":
				blockStatus = "ASSIGNED_FE_FOR_DROP";
				flowStatus = "DROP_MOB";
				break;
			default:
				blockStatus = actionStatus;
			}

			if (flowId.equals("PickAndDropTwoWay")) {
				misReportBlockRepository.updateTicketBasicPriority(String.valueOf(requestMap.get("processId")),
						String.valueOf(resultSet.get("newTicketPriority")));
			}

			// "flowId": "PickAndDropTwoWay",
			// "newTicketPriority": "high",

			// wFeAssign
			misReportBlockRepository.updateBlockMISWFeAssign(String.valueOf(resultSet.get("feId")),
					String.valueOf(resultSet.get("feType")), ticketBlockId);
			misReportBlockRepository.updateBlockOpsWFeAssign(String.valueOf(resultSet.get("feId")),
					String.valueOf(resultSet.get("feType")), ticketBlockId);

			if (actionStatus.equals("ASSIGNED_FE_FOR_PICKUP")
					|| actionStatus.equals("ASSIGNED_FE_FOR_SERVICE_PICKUP")) {
				misReportBlockRepository.updateBlockMISWPickupFeAssign(String.valueOf(resultSet.get("feId")),
						String.valueOf(resultSet.get("feType")), String.valueOf(resultSet.get("feName")),
						ticketBlockId);
			} else if (actionStatus.equals("ASSIGNED_FE_FOR_DROP")
					|| actionStatus.equals("ASSIGNED_FE_FOR_SERVICE_DROP")) {
				misReportBlockRepository.updateBlockMISWDropFeAssign(String.valueOf(resultSet.get("feId")),
						String.valueOf(resultSet.get("feType")), String.valueOf(resultSet.get("feName")),
						ticketBlockId);
			}

			// mis
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// Ops
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wPackingMaterial(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
			String processAt = String.valueOf(resultSet.get("processAt"));

			String retailerId = String.valueOf((requestMap.get("retailerId")));
			if(retailerId.equals("drp1657114589530TZ70b556867f634bce8214ba67799d8cce") || retailerId.equals("drp1657114417015TZf2b50e43392440ddbcf83fa2d5f88fc0")) { 
				
			}else {
				if (misReportProdRepository.check(ticketBlockId) <= 0) {
					
					respAction.put("processStatus", "failed");
					respAction.put("actionMsg", "Product barcode is not Assigned");
					return respAction;
					// "Product barcode is not Assigned";
			
				}
			}

//		count = misReportBlockRepository.check(ticketBlockId);
//		count = misReportTicketsRepository.check(ticketBlockId);

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = "";
			String flowStatus = "PICK_WEB";
			switch (actionStatus) {
			case "HANDED_OVER_PACKING_MATERIALS":
				blockStatus = "HANDED_OVER_PACKING_MATERIALS";
				break;
			default:
				blockStatus = "";
			}

			// mis
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// Ops
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;

	}

	// updateBarcodeStatus(String barcode, String status) "10issued" Function To
	// updadte
	// "10issued" Function To update

	void updateBarcodeStatus(String barcode, String status) {

		misReportBlockRepository.updateStatusBasedonBarcode(barcode, status);

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wBarcodeBlockAssign(Map<String, Object> requestMap, String ticketBlockId)
			throws JSONException {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String actionStatus = String.valueOf(resultSet.get("actionStatus"));

			// Check the barcode Availability Status
			String barcodeStatus = this.checkBarcodeStatus(String.valueOf(resultSet.get("barcode")).trim());

			if (!barcodeStatus.equals("available")) {
				respAction.put("processStatus", "failed");
				respAction.put("actionMsg", barcodeStatus);
				return respAction;
			}

			// wBarcodeProdAssign

			/*
			 * =============================================================================
			 * ==============================Need Attention get current values
			 * zzz_ticket_mis_report_block_ convert json to Array put barcode into Array
			 * convert to json update
			 */

			String barcode_db = misReportBlockRepository.getBlockBarcode(ticketBlockId);

			String barcodeFinal = "";
			if (barcode_db == null) {
				barcode_db = "";
			} else {
				barcode_db = barcode_db + ", ";
			}

			barcodeFinal = barcode_db.trim() + String.valueOf(resultSet.get("barcode")).trim();

			misReportBlockRepository.updateBarcode(ticketBlockId, String.valueOf(barcodeFinal));

//		misReportBlockRepository.updateBlockMISWBarcodeBlockAssign(String.valueOf(resultSet.get("barcode")),
//				ticketBlockId);

			/*
			 * =============================================================================
			 * ==============================Need Attention Build insert Object Save into
			 * ticket_process_barcodes_block_ table
			 */

			TicketProcessBarcodesBlock block = new TicketProcessBarcodesBlock();

			block.setTicketBlockId(ticketBlockId);
			block.setBlockBarcode(String.valueOf(barcodeFinal));

			setObserver(block);

			ticketProcessBarcodesBlock.save(block);

			// Update Status Based on Barcode
			updateBarcodeStatus(String.valueOf(resultSet.get("barcode")).trim(), "10issued");

//		misReportBlockRepository.updateBlockOpsWBarcodeBlockAssign(String.valueOf(resultSet.get("barcode")),
//				ticketBlockId);

//			String blockStatus = actionStatus;
			// mis
			misReportBlockRepository.updateBlockMISActionStatus(actionStatus, ticketBlockId);

			// Ops
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", "");
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wBarcodeTicketAssign(Map<String, Object> requestMap, String processId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "PICK_WEB";

			// Check the barcode Availability Status
			String barcodeStatus = this.checkBarcodeStatus(String.valueOf(resultSet.get("barcode")).trim());

			if (!barcodeStatus.equals("available")) {
				respAction.put("processStatus", "failed");
				respAction.put("actionMsg", barcodeStatus);
				return respAction;
			}

			/*
			 * =============================================================================
			 * ==============================Need Attention get current values
			 * zzz_ticket_mis_report_ticket_ convert json to Array put barcode into Array
			 * convert to json update
			 */

			String ticketBarcode_db = misReportTicketsRepository.getTicketBarcode(processId);

			String barcodeFinal = "";
			if (ticketBarcode_db == null) {
				ticketBarcode_db = "";
			} else {
				ticketBarcode_db = ticketBarcode_db + ", ";
			}
			String inputTicketBarcode = String.valueOf(resultSet.get("barcode")).trim();
			barcodeFinal = ticketBarcode_db.trim() + String.valueOf(resultSet.get("barcode")).trim();

			misReportTicketsRepository.updateTicketBarcode(processId, String.valueOf(barcodeFinal));

			/*
			 * =============================================================================
			 * ==============================Need Attention Build insert Object Save into
			 * ticket_process_barcodes_ticket_ table
			 */

			TicketProcessBarcodesTicket ticket = new TicketProcessBarcodesTicket();

			ticket.setProcessId(processId);
			ticket.setTicketBarcode(String.valueOf(barcodeFinal));

			setObserver(ticket);

			ticketProcessBarcodesTicket.save(ticket);

			// Update Status Based on Barcode
			updateBarcodeStatus(inputTicketBarcode, "10issued");

			String blockStatus = actionStatus;
			// mis
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, processId,
					getDateAndTime());

			// Ops
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, actionStatus, actionStatus, processId);

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;

	}

	private String insertTicketBlockRemarks(String ticketBlockId, String remarkMsg) {
//		table: ticket_process_remarks 

		ticketProcessRemarks.setProcessRemarkId(getKey("rem"));
		ticketProcessRemarks.setTicketBlockId(ticketBlockId);
		ticketProcessRemarks.setRemark(remarkMsg);
		setObserverCreate(ticketProcessRemarks);
		ticketProcessRemarksDao.save(ticketProcessRemarks);
		return "success";
	}

	private String updateDstHubId(String ticketBlockId, String dstHubId) {
//		table:  ticket_process_blocks 

		Optional<TicketProcessBlocks> curValue = ticketProcessBlocksDao.findById(ticketBlockId);
		if (!curValue.isPresent()) {
			return "Id not found";
		}

		ticketProcessBlocksDao.updateDstHubId(ticketBlockId, dstHubId);
		return "success";
	}

	private String updateOrInsertProdBarcode(String ticketProductId, String barcode) {

		// Get if row exist
		if (misReportProdRepository.getTicketProductIdCount(ticketProductId) > 0) {
			// Update
			misReportBlockRepository.updateBlockOpsWBarcodeProdAssign(barcode, ticketProductId);
		} else {
			ticketProcessProducts.setPrdBarcode(barcode);
			ticketProcessProducts.setTicketProductId(ticketProductId);
			setObserver(ticketProcessProducts);
			ticketProcessProductsDao.save(ticketProcessProducts);
		}

		return "success";
	}

//	private String chooseDropOrLinehaulOrHORTO(String flowId, String ticketBlockId, String srcPincode,
//			String dstPincode) throws ClientProtocolException, IOException, JSONException {
//
//		String srcHubId = null, dstHubId = null;
//
//		Map<String, String> blockDetails = misReportBlockRepository.getCurSrcDstHubByBlockId(ticketBlockId);
//		srcHubId = String.valueOf(blockDetails.get("SRC_HUB_ID"));
//		dstHubId = String.valueOf(blockDetails.get("DST_HUB_ID"));
//

//
//		if (dstHubId.equals("null")) {

//			String url = confGetPincodeInfo(dstPincode);
//			JSONObject apiRespJSON = this.callApiGet(url);
//
//			if (apiRespJSON.get("msg").equals("Success")) {
//				JSONObject respPincodeDetails = new JSONObject(String.valueOf(apiRespJSON.get("data")));
//
//				if (respPincodeDetails.get("pincode").equals("000000")) {
//					this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: destPincode not available");
//					return "ASSIGN_TO_HO";
//
//				}
//				dstHubId = String.valueOf(respPincodeDetails.get("hubId"));
//				this.updateDstHubId(ticketBlockId, dstHubId);
//
//				if (respPincodeDetails.get("serviceType").equals("NDA")) {
//					this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: destPincode is NDA");
//					return "ASSIGN_TO_HO";
//				}
//			}
//		}
//
//		if (srcHubId.equals(dstHubId)) {
//
//			if (flowId.equals("PickAndDropTwoWay")) {
//				return "CHOOSE_FE_FOR_DROP_ASC";
//			}
//
//			return "CHOOSE_FE_FOR_DROP";
//
//		}
//		return "LINEHAUL";
//
//	}
	private String chooseDropOrLinehaulOrHORTO(String flowId, String ticketBlockId, String srcPincode,
			String dstPincode) throws ClientProtocolException, IOException, JSONException {

		String srcHubId = null, dstHubId = null;

		Map<String, String> blockDetails = misReportBlockRepository.getCurSrcDstHubByBlockId(ticketBlockId);
		srcHubId = String.valueOf(blockDetails.get("SRC_HUB_ID"));
		dstHubId = String.valueOf(blockDetails.get("DST_HUB_ID"));

		if (srcHubId.equals("null")) {

			String url = confGetPincodeInfo(srcPincode);
			JSONObject apiRespJSON = this.callApiGet(url);

			if (apiRespJSON.get("msg").equals("Success")) {
				JSONObject respPincodeDetails = new JSONObject(String.valueOf(apiRespJSON.get("data")));

				if (respPincodeDetails.get("pincode").equals("000000")) {
					this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: srcPincode not available");
					return "ASSIGN_TO_HO";

				}

//				srcHubId = String.valueOf(respPincodeDetails.get("hubId"));
//				this.updateDstHubId(ticketBlockId, srcHubId);

//				misReportBlockRepository.updateBlockOpsWRtoLinehaulHubIdSrc(ticketBlockId);

				if (respPincodeDetails.get("serviceType").equals("NDA")) {
					this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: destPincode is NDA");
					return "ASSIGN_TO_HO";
				}
			}
		}

		if (srcHubId.equals(dstHubId)) {

			if (flowId.equals("PickAndDropTwoWay")) {
				return "CHOOSE_FE_FOR_DROP_ASC";
			}

			return "CHOOSE_FE_FOR_DROP";

		}
		ticketProcessBlocksDao.updateSrcHubId(ticketBlockId, dstHubId);
		ticketProcessBlocksDao.updateDstHubId(ticketBlockId, srcHubId);
		return "LINEHAUL";

	}

	private String chooseDropOrLinehaulOrHO(String flowId, String ticketBlockId, String srcPincode, String dstPincode)
			throws ClientProtocolException, IOException, JSONException {

		String srcHubId = null, dstHubId = null;

		Map<String, String> blockDetails = misReportBlockRepository.getCurSrcDstHubByBlockId(ticketBlockId);
		srcHubId = String.valueOf(blockDetails.get("SRC_HUB_ID"));
		dstHubId = String.valueOf(blockDetails.get("DST_HUB_ID"));

		if (dstHubId.equals("null")) {

			String url = confGetPincodeInfo(dstPincode);
			JSONObject apiRespJSON = this.callApiGet(url);

			if (apiRespJSON.get("msg").equals("Success")) {
				JSONObject respPincodeDetails = new JSONObject(String.valueOf(apiRespJSON.get("data")));

				if (respPincodeDetails.get("pincode").equals("000000")) {
					this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: destPincode not available");
					return "ASSIGN_TO_HO";

				}
				dstHubId = String.valueOf(respPincodeDetails.get("hubId"));
				this.updateDstHubId(ticketBlockId, dstHubId);

				if (respPincodeDetails.get("serviceType").equals("NDA")) {
					this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: destPincode is NDA");
					return "ASSIGN_TO_HO";
				}
			}
		}

		if (srcHubId.equals(dstHubId)) {

			if (flowId.equals("PickAndDropTwoWay")) {
				return "CHOOSE_FE_FOR_DROP_ASC";
			}

			return "CHOOSE_FE_FOR_DROP";

		}
		return "LINEHAUL";

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wPickupConfirm(Map<String, Object> requestMap, String ticketBlockId)
			throws ClientProtocolException, IOException, JSONException {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "PICK_WEB";

			String srcPincode = String.valueOf(resultSet.get("srcPincode"));
			String dstPincode = String.valueOf(resultSet.get("dstPincode"));
			String flowId = String.valueOf(resultSet.get("flowId"));

			switch (actionStatus) {
			case "PICKUP_CONFIRMED":
				blockStatus = this.chooseDropOrLinehaulOrHO(flowId, ticketBlockId, srcPincode, dstPincode);
				break;
			default:
				blockStatus = actionStatus;
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wRtoInitiate(Map<String, Object> requestMap, String ticketBlockId)
			throws ClientProtocolException, IOException, JSONException {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String flowStatus = "PICK_WEB";

			String srcPincode = String.valueOf(resultSet.get("srcPincode"));
			String dstPincode = String.valueOf(resultSet.get("dstPincode"));
			String flowId = String.valueOf(resultSet.get("flowId"));

			switch (actionStatus) {
			case "RTO_INITIATE":
				blockStatus = this.chooseDropOrLinehaulOrHORTO(flowId, ticketBlockId, srcPincode, dstPincode);
				break;
			default:
				blockStatus = actionStatus;
			}

			// Change the Direction
			misReportBlockRepository.updateBlockOpswRtoInitiateDirection(ticketBlockId, "RTO");

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wLinehaul(Map<String, Object> requestMap, String ticketBlockId) throws JSONException {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";
			String destinationType = String.valueOf(resultSet.get("destinationType"));
			switch (actionStatus) {
			case "SHIPMENT_CREATED":
				blockStatus = "LINEHAUL_IN_TRANSIT";
				break;
			default:
				blockStatus = actionStatus;
			}

			JSONArray ticketBlockIds = (JSONArray) resultSet.get("ticketBlockIds");
			String linehaulAwb = String.valueOf(resultSet.get("shipmentNo"));
			String linehaulAwbDate = getDateAndTime();

			for (int i = 0; i < ticketBlockIds.length(); i++) {
				String r = String.valueOf(ticketBlockIds.get(i));
				Map<?, ?> idsMap = new Gson().fromJson(r, Map.class);
				String rowTicketBlockId = String.valueOf(idsMap.get("blockId"));

				// Update Hub type != hub update HubId = dstHubId
				if (destinationType.equals("customer")) {
					misReportBlockRepository.updateBlockOpsWLinehaulHubIdSrc(rowTicketBlockId);
				} else {
					misReportBlockRepository.updateBlockOpsWLinehaulHubIdDst(rowTicketBlockId);
				}

				// Update ShipmentNo & Date

				misReportBlockRepository.updateBlockMISShipmentNO(linehaulAwb, linehaulAwbDate, rowTicketBlockId);
				misReportBlockRepository.updateBlockOpsShipmentNO(linehaulAwb, linehaulAwbDate, rowTicketBlockId);

				// MIS
				misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, rowTicketBlockId,
						getDateAndTime());

				// Ops
				misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, rowTicketBlockId);
				misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, rowTicketBlockId);

			}
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wLinehaulVerification(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";
			switch (actionStatus) {
			case "05RECEIVED":
				blockStatus = "CHOOSE_FE_FOR_DROP";
				break;
			case "15MISSING":
				this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: Shipment is Missing ");
				blockStatus = "ASSIGN_TO_HO";
				break;
			case "10DAMAGE":
				this.insertTicketBlockRemarks(ticketBlockId, "Assigned to HO: Shipment received is damaged ");
				blockStatus = "ASSIGN_TO_HO";
				break;
			case "20DIRECT_DELIVERY":
				this.insertTicketBlockRemarks(ticketBlockId,
						"Assigned Source Hub: Shipment is delivered directly to Client");
				blockStatus = "DROP_CONFIRMED";
				break;
			default:
				blockStatus = actionStatus;
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wDropConfirm(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";

			String blockStatus = actionStatus;
			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, actionStatus, actionStatus,
					ticketBlockId);
			
			//UPDATE PLAYER FE ID TO NULL
			String value="null";
			misReportBlockRepository.updatePlayerFeIdToNull(value, ticketBlockId);
			;
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wBlockClosed(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";
			misReportBlockRepository.updateBlockMISWBlockClosed(String.valueOf(getDate()),
					String.valueOf(getDateAndTime()), String.valueOf(resultSet.get("closeByType")),
					String.valueOf(resultSet.get("closeById")), String.valueOf(resultSet.get("closeByName")),
					String.valueOf(resultSet.get("closingRemarks")),
					String.valueOf(resultSet.get("ticketClosingStatus")), ticketBlockId);

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, actionStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wPartReceived(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
//			String flowId = String.valueOf(resultSet.get("flowId"));
			String flowStatus = "PICK_WEB";

			String docketNo = String.valueOf(resultSet.get("docketNo"));

			switch (actionStatus) {
			case "PART_RECEIVED":
				blockStatus = "PART_RECEIVED";

				break;
			default:
				blockStatus = actionStatus;
			}

			misReportBlockRepository.updateBlockOpsMPickupDocketNo(docketNo, ticketBlockId);
			misReportBlockRepository.updateBlockMISMPickupDocketNo(docketNo, ticketBlockId);

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mPartMissing(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowId = String.valueOf(resultSet.get("flowId"));
			String flowStatus = "PICK_WEB";

			String partAdd1 = "NULL";
			switch (actionStatus) {
			case "PART_MISSING":
				blockStatus = "PART_MISSING";
				partAdd1 = String.valueOf(resultSet.get("partAdd1"));
				break;
			default:
				blockStatus = actionStatus;
			}

//		// mPickup
//		misReportBlockRepository.updateBlockMISMPickup(String.valueOf(resultSet.get("pickUpDate")),
//				String.valueOf(resultSet.get("pickUpTime")), String.valueOf(resultSet.get("pickUpFeType")),
//				String.valueOf(resultSet.get("pickUpFeId")), String.valueOf(resultSet.get("pickUpFeName")),
//				String.valueOf(resultSet.get("pickUpRemarks")), ticketBlockId);

			if (!partAdd1.equals("NULL")) {
				misReportBlockRepository.updateBlockOpsMPickupPartAdd1(partAdd1, ticketBlockId);
				misReportBlockRepository.updateBlockMISMPickupPartAdd1(partAdd1, ticketBlockId);
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mPickup(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowId = String.valueOf(resultSet.get("flowId"));
			String flowStatus = "DROP_WEB";

			String partAdd1 = "NULL";
			switch (actionStatus) {
			case "PICKUP_DONE":
				blockStatus = "PICKUP_DONE";
				break;
			case "PICKUP_DONE_ASC":
				blockStatus = "PICKUP_DONE_ASC";
				break;
			case "REJECT_SERVICE_FE_AT_PICKUP":
				blockStatus = "REJECT_SERVICE_FE_AT_PICKUP";
				flowStatus = "PICK_WEB";
				break;
			case "INSTALL_SERVICE_DONE":
				blockStatus = "INSTALL_SERVICE_DONE";
				flowStatus = "PICK_WEB";
				break;
			case "PART_MISSING":
				blockStatus = "PART_MISSING";
				flowStatus = "PICK_WEB";
				partAdd1 = String.valueOf(resultSet.get("partAdd1"));
				break;
			case "PICK_UP_DONE_EXCHANGE_SUCCESSFUL":
				if (flowId.equals("AdvanceExchange")) {
					blockStatus = "PICK_UP_DONE_EXCHANGE_SUCCESSFULLY";
				}
				break;
			case "OLD_PRODUCT_DAMAGED_EXCHANGE_CANCELLED":
				if (flowId.equals("AdvanceExchange")) {
					blockStatus = "OLD_PRODUCT_DAMAGED_EXCHANGE_CANCELLED";
				}
				break;

			default:
				blockStatus = actionStatus;
			}

			if (blockStatus.equals("PICKUP_DONE") || blockStatus.equals("PICKUP_DONE_ASC")
					|| blockStatus.equals("INSTALL_SERVICE_DONE")
					|| blockStatus.equals("PICK_UP_DONE_EXCHANGE_SUCCESSFULLY")) {

				// mPickup
				misReportBlockRepository.updateBlockMISMPickup(String.valueOf(resultSet.get("pickUpDate")),
						String.valueOf(resultSet.get("pickUpTime")), String.valueOf(resultSet.get("pickUpRemarks")),
						ticketBlockId);
			}

			if (!partAdd1.equals("NULL")) {
				misReportBlockRepository.updateBlockOpsMPickupPartAdd1(partAdd1, ticketBlockId);
				misReportBlockRepository.updateBlockMISMPickupPartAdd1(partAdd1, ticketBlockId);
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mDrop(Map<String, Object> requestMap, String ticketBlockId) throws ParseException {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";
			switch (actionStatus) {
			case "DROP_DONE":
				blockStatus = "DROP_DONE";
				break;
			case "DROP_DONE_ASC":
				blockStatus = "DROP_DONE_ASC";
				break;
			default:
				blockStatus = actionStatus;
			}

			if (blockStatus.equals("DROP_DONE") || blockStatus.equals("DROP_DONE_ASC")) {
				misReportBlockRepository.updateBlockMISMDrop(String.valueOf(resultSet.get("dropDate")),
						String.valueOf(resultSet.get("dropTime")), String.valueOf(resultSet.get("dropRemarks")),
						ticketBlockId);
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mBasicEval(Map<String, Object> requestMap, String ticketProductId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));

			String flowId = String.valueOf(resultSet.get("flowId"));
			if (flowId.equals("AdvanceExchange")) {

				// Update ticket_basic_products table
				misReportBlockRepository.updateProdOpsMBasicEvalIdNo(
						String.valueOf(resultSet.get("newIdentificationNo")), ticketProductId);
			}
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mPhysicEval(Map<String, Object> requestMap, String ticketBlockId,
			String ticketProductId) throws JSONException {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));

			switch (actionStatus) {
			case "QC_PASS":
				blockStatus = "QC_PASS";
				break;
			case "QC_FAIL":
				blockStatus = "QC_FAIL";
				break;
			default:
				blockStatus = actionStatus;
			}

			if (actionStatus.equals("QC_FAIL")) {

				List<Map> list = (List<Map>) resultSet.get("evaluationQA");

				for (int i = 0; i < list.size(); i++) {
					Map<String, String> qc = list.get(i);

					String qcRemarks = qc.get("label") + "|" + qc.get("data");
//				todo update method

					switch (i) {

					case 0:
						misReportBlockRepository.updateCusField1(ticketBlockId, qcRemarks);
						break;
					case 1:
						misReportBlockRepository.updateCusField2(ticketBlockId, qcRemarks);
						break;
					case 2:
						misReportBlockRepository.updateCusField3(ticketBlockId, qcRemarks);
						break;
					case 3:
						misReportBlockRepository.updateCusField4(ticketBlockId, qcRemarks);
						break;
					case 4:
						misReportBlockRepository.updateCusField5(ticketBlockId, qcRemarks);
						break;
					case 5:
						misReportBlockRepository.updateCusField6(ticketBlockId, qcRemarks);
						break;
					case 6:
						misReportBlockRepository.updateCusField7(ticketBlockId, qcRemarks);
						break;
					case 7:
						misReportBlockRepository.updateCusField8(ticketBlockId, qcRemarks);
						break;
					case 8:
						misReportBlockRepository.updateCusField9(ticketBlockId, qcRemarks);
						break;
					case 9:
						misReportBlockRepository.updateCusField10(ticketBlockId, qcRemarks);
						break;

					}

				}

			}

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mRTORequest(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";

			String blockStatus = "";

			switch (actionStatus) {
			case "RTO_REQUEST":
				blockStatus = "RTO_REQUEST";
				break;
			default:
				blockStatus = actionStatus;
			}

			// Update Direction to RTO in ops & mis

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, ticketBlockId,
					getDateAndTime());
			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, actionStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wRTOInitiate(Map<String, Object> requestMap, String ticketBlockId)
			throws ClientProtocolException, IOException, JSONException {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";

			String srcPincode = String.valueOf(resultSet.get("srcPincode"));
			String dstPincode = String.valueOf(resultSet.get("dstPincode"));
			String flowId = String.valueOf(resultSet.get("flowId"));

			String blockStatus = "";

			switch (actionStatus) {
			case "RTO_INITIATE":
				blockStatus = this.chooseDropOrLinehaulOrHORTO(flowId, ticketBlockId, srcPincode, dstPincode);
				break;
			default:
				blockStatus = actionStatus;
			}

			// Change the Direction
			misReportBlockRepository.updateBlockOpswRtoInitiateDirection(ticketBlockId, "RTO");

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());
			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wRTOReject(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";

			String blockStatus = "";

			switch (actionStatus) {
			case "RTO_REJECT":
				blockStatus = "RTO_REJECT";
				break;
			default:
				blockStatus = actionStatus;
			}

			// Update Direction to RTO in ops & mis

//		misReportBlockRepository.updateDirectionToRTOInTPB(ticketBlockId);
//		misReportBlockRepository.updateDirectionToRTOInTMRB(ticketBlockId);

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, ticketBlockId,
					getDateAndTime());
			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, actionStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mClosed(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";

			String blockStatus = actionStatus;
			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, actionStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mCustStatus(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String flowStatus = "DROP_MOB";

			String processAt = String.valueOf(resultSet.get("processAt"));
			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mCustDetails(Map<String, Object> requestMap, String ticketBlockId) {

		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Integer count;

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));

			String processAt = String.valueOf(resultSet.get("processAt"));

			if (processAt.equals("null")) {
				processAt = getDateAndTime();
			}

			if (flowStatus.equals("") || flowStatus.equals(null)) {
				flowStatus = "PICK_MOB";
			}

			// TCQ Attempts
			if (!actionStatus.equals("AVAILABLE")) {
				count = misReportBlockRepository.getCount(ticketBlockId);
				count = count + 1;

				String remark = String.valueOf(resultSet.get("reScheduleDateTime")) + " | "
						+ String.valueOf(resultSet.get("remark"));

				switch (count) {

				case 1:
					misReportBlockRepository.updateAttempt1(ticketBlockId, remark, processAt, count);
					break;
				case 2:
					misReportBlockRepository.updateAttempt2(ticketBlockId, remark, processAt, count);
					break;
				case 3:
					misReportBlockRepository.updateAttempt3(ticketBlockId, remark, processAt, count);
					break;
				case 4:
					misReportBlockRepository.updateAttempt4(ticketBlockId, remark, processAt, count);
					break;
				default:
					misReportBlockRepository.updateAttempt5(ticketBlockId, remark, processAt, count);
					break;
				}

			}

			switch (actionStatus) {
			case "AVAILABLE":
				blockStatus = "AVAILABLE_FOR_PROCESS";
				break;
			case "RESCHEDULE_FE_AT_PICKUP":
				blockStatus = "RESCHEDULE_FE_AT_PICKUP";
				flowStatus = "PICK_WEB";
				break;
			case "RELEASE_FE_AT_PICKUP":
				blockStatus = "HANDED_OVER_PACKING_MATERIALS";
				flowStatus = "PICK_WEB";
				break;
			case "RELEASE_FE_AT_SERVICE_PICKUP":
				blockStatus = "DROP_DONE_ASC";
				flowStatus = "PICK_WEB";
				break;
			case "REJECT_SERVICE_FE_AT_PICKUP":
				blockStatus = "REJECT_SERVICE_FE_AT_PICKUP";
				flowStatus = "PICK_WEB";
				break;
			case "RESCHEDULE_FE_AT_DROP":
				blockStatus = "RESCHEDULE_FE_AT_DROP";
				flowStatus = "DROP_WEB";
				break;
			case "RELEASE_FE_AT_DROP":
				blockStatus = "CHOOSE_FE_FOR_DROP";
				flowStatus = "DROP_WEB";
				break;
			case "RELEASE_FE_AT_SERVICE_DROP":
				blockStatus = "CHOOSE_FE_FOR_DROP_ASC";
				flowStatus = "DROP_WEB";
				break;
			case "RELEASE_FE_AT_CUSTOMER_DROP":
				blockStatus = "PICKUP_DONE_ASC";
				flowStatus = "DROP_WEB";
				break;
			case "REJECT_SERVICE_FE_AT_DROP":
				blockStatus = "REJECT_SERVICE_FE_AT_DROP";
				flowStatus = "DROP_WEB";
				break;
			default:
				blockStatus = actionStatus;
			}

			// MIS
			if (!blockStatus.equals("AVAILABLE_FOR_PROCESS")) {
				misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
						getDateAndTime());
			}

			// TCQ Attempts

//			if (!actionStatus.equals("AVAILABLE")) {
//				count = misReportBlockRepository.getCount(ticketBlockId);
//				count = count + 1;
//				String remark =null;
//				
//				if (actionStatus.equals("REJECT_SERVICE_FE_AT_PICKUP") || actionStatus.equals("REJECT_SERVICE_FE_AT_DROP")) {
//					remark = String.valueOf(resultSet.get("remark"));
//				}else {
//				remark = String.valueOf(resultSet.get("reScheduleDateTime")) + " | "
//						+ String.valueOf(resultSet.get("remark"));
//				}
//				
////				String remark = String.valueOf(resultSet.get("reScheduleDateTime")) + " | "
////						+ String.valueOf(resultSet.get("remark"));
////				String remark = String.valueOf(resultSet.get("remark"));
//				String dateTime = processAt;
//				
//				switch (count) {
//
//				case 1:
//					misReportBlockRepository.updateAttempt1(ticketBlockId, remark, dateTime,count);
//					break;
//				case 2:
//					misReportBlockRepository.updateAttempt2(ticketBlockId, remark, dateTime,count);
//					break;
//				case 3:
//					misReportBlockRepository.updateAttempt3(ticketBlockId, remark, dateTime,count);
//					break;
//				case 4:
//					misReportBlockRepository.updateAttempt4(ticketBlockId, remark, dateTime,count);
//					break;
//				default:
//					misReportBlockRepository.updateAttempt5(ticketBlockId, remark, dateTime,count);
//					break;
//				}
//			
//		}

			// OPS
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mServicePickup(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mServiceDrop(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "DROP_WEB";

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// Ops
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

//	@SuppressWarnings("unchecked")
//	private Map<String, String> mBuyBackStatus(Map<String, Object> requestMap, String ticketBlockId,
//			String ticketProductId) {
//		Map<String, String> respAction = new HashMap<String, String>();
//		try {
//			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
//
//			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
//			String blockStatus = actionStatus;
//			String processAt = String.valueOf(resultSet.get("processAt"));
//			String flowStatus = String.valueOf(requestMap.get("flowStatus"));
//
//			if (flowStatus.equals("") || flowStatus.equals(null)) {
//				flowStatus = "PICK_MOB";
//			}
//
//			switch (actionStatus) {
//			case "ACCEPTED":
//				blockStatus = "CUSTOMER_AGREED_WITH_THE_VALUE";
//				break;
//			case "REJECTED":
//				blockStatus = "CUSTOMER_DISAGREE_WITH_THE_VALUE";
//				flowStatus = "PICK_MOB";
//				break;
//			case "RE_QUOTE":
//				blockStatus = "CUSTOMER_ASK_FOR_REQUOTE";
//				flowStatus = "PICK_MOB";
//				break;
//			default:
//				blockStatus = actionStatus;
//			}
//
//			// MIS
//			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
//					getDateAndTime());
//			misReportBlockRepository.updateProdMISActionStatus(actionStatus, ticketProductId);
//			misReportBlockRepository.updateProdMISActionStatus(blockStatus, ticketProductId);
//
//			// Ops
//			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
//			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
//
//			misReportBlockRepository.updateProdOpsActionStatus(blockStatus, ticketProductId);
//			respAction.put("processStatus", "success");
//			respAction.put("actionStatus", actionStatus);
//			respAction.put("blockStatus", blockStatus);
//			respAction.put("processAt", processAt);
//			respAction.put("actionMsg", "Action Message");
//		} catch (Exception e) {
//			respAction.put("processStatus", "failed");
//			e.printStackTrace();
//		}
//		return respAction;
//
//	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mReQuote(Map<String, Object> requestMap, String ticketBlockId, String ticketProductId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = "PICK_MOB";
			switch (actionStatus) {
			case "ACCEPTED_REQUOTE":
				blockStatus = "CUSTOMER_AGREED_WITH_THE_REQUOTE_VALUE";
				break;
			case "REJECTED_REQUOTE":
				blockStatus = "CUSTOMER_DISAGREE_WITH_THE_REQUOTE_VALUE";
				flowStatus = "PICK_MOB";
				break;
			default:
				blockStatus = actionStatus;
			}

			// mReQuote Update Requote value
			misReportBlockRepository.updateProdMReQuoteValue(String.valueOf(resultSet.get("valueOffered")),
					ticketProductId);

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			misReportBlockRepository.updateProdMISActionStatus(blockStatus, ticketProductId);

			// Ops
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

			misReportBlockRepository.updateProdOpsActionStatus(blockStatus, ticketProductId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;
	}

	private String checkBarcodeStatus(String barcode) {

		Map<String, String> barcodeDetails = misReportBlockRepository.getBarcodeDetails(barcode);
		String returnBarcode = barcodeDetails.get("STATUS");

		if (returnBarcode.equals("00available")) {
			return "available";
		} else {
			return "Invalid Barcode";
		}

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wBarcodeProdAssign(Map<String, Object> requestMap, String ticketProductId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = "";
			String processAt = String.valueOf(resultSet.get("processAt"));
			// Check the barcode Availability Status
			String barcodeStatus = this.checkBarcodeStatus(String.valueOf(resultSet.get("barcode")).trim());

			if (!barcodeStatus.equals("available")) {
				respAction.put("processStatus", "failed");
				respAction.put("actionMsg", barcodeStatus);
				return respAction;
			}

			// wBarcodeProdAssign
			misReportBlockRepository.updateBlockMISWBarcodeProdAssign(String.valueOf(resultSet.get("barcode")).trim(),
					String.valueOf(resultSet.get("ticketProductId")));
			updateOrInsertProdBarcode(ticketProductId, String.valueOf(resultSet.get("barcode")).trim());

			// Update Status Based on Barcode
			updateBarcodeStatus(String.valueOf(resultSet.get("barcode")).trim(), "10issued");

			// MIS
			misReportBlockRepository.updateProdMISActionStatus(actionStatus, ticketProductId);

			// Ops
			misReportBlockRepository.updateProdOpsActionStatus(actionStatus, ticketProductId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;

	}

	@SuppressWarnings("unchecked")
	public Map<String, String> mFeProcessStart(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String.valueOf(resultSet.get("flowId"));
			String processType = String.valueOf(resultSet.get("processType"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));

			String processDateTime = String.valueOf(resultSet.get("processDateTime"));

			System.out.println("**************PROCESS DATE TIME*************************" + processDateTime);

			if (processType.equals("P1") || processType.equals("D1")) {
				flowStatus = "PICK_MOB";

			}

			switch (processType) {
			case "P1":

				// Store to Tables
				misReportBlockRepository.updateticketrepotP1Start(processDateTime, ticketBlockId);

				// misReportBlockRepository.updateticketrepotP1END(processDateTime,ticketBlockId);

				break;
			case "P2":
//			Store to Tables

				misReportBlockRepository.updateticketrepotP2Start(processDateTime, ticketBlockId);
				// misReportBlockRepository.updateticketrepotP2END(processDateTime,ticketBlockId);
				break;
			case "D1":
//			Store to Tables
				misReportBlockRepository.updateticketrepotD1Start(processDateTime, ticketBlockId);

				// misReportBlockRepository.updateticketrepotD1END(processDateTime,ticketBlockId);
				break;
			case "D2":
//			Store to Tables
				misReportBlockRepository.updateticketrepotD2Start(processDateTime, ticketBlockId);

				// misReportBlockRepository.updateticketrepotD2END(processDateTime,ticketBlockId);
				break;
			default:
				flowStatus = actionStatus;
			}
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unused")
	private Map<String, String> mFeProcessEnd(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String.valueOf(resultSet.get("flowId"));
			String processType = String.valueOf(resultSet.get("processType"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));

			String processDateTime = String.valueOf(resultSet.get("processDateTime"));

			if (processType.equals("P1") || processType.equals("D1")) {
				flowStatus = "DROP_MOB";
			}
			switch (processType) {
			case "P1":
				misReportBlockRepository.updateticketrepotP1END(processDateTime, ticketBlockId);
				// misReportBlockRepository.updateticketprocessP1End(processDateTime);

				break;
			case "P2":
//			Store to Tables
				misReportBlockRepository.updateticketrepotP2END(processDateTime, ticketBlockId);
				// misReportBlockRepository.updateticketprocessP2End(processDateTime);
				break;
			case "D1":
//			Store to Tables
				misReportBlockRepository.updateticketrepotD1END(processDateTime, ticketBlockId);
				// misReportBlockRepository.updateticketprocessD1End(processDateTime);
				break;
			case "D2":
//			Store to Tables
				misReportBlockRepository.updateticketrepotD2END(processDateTime, ticketBlockId);
				// misReportBlockRepository.updateticketprocessD2End(processDateTime);
				break;
			default:
				flowStatus = actionStatus;
			}
			String blockStatus = actionStatus;
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	private Map<String, String> defaultSuccessResp(Map<String, Object> requestMap, Map<String, String> respAction) {
		Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
		String actionStatus = String.valueOf(resultSet.get("actionStatus"));
		String processAt = String.valueOf(resultSet.get("processAt"));
		String blockStatus = "";
		respAction.put("processStatus", "success");
		respAction.put("actionStatus", actionStatus);
		respAction.put("blockStatus", blockStatus);
		respAction.put("processAt", processAt);
		respAction.put("actionMsg", "Block Update Status is not Required");
		return respAction;
	}

	public Map<String, Object> updateBlockReport(Map<String, Object> requestMap)
			throws JSONException, ClientProtocolException, IOException, ParseException {

		/**
		 * Params from payload
		 */
		String syncId = String.valueOf(requestMap.get("syncId"));
		String processId = String.valueOf(requestMap.get("processId"));
		String ticketBlockId = String.valueOf(requestMap.get("ticketBlockId"));
		String ticketProductId = String.valueOf(requestMap.get("ticketProductId"));
		String ticketNo = String.valueOf(requestMap.get("ticketNo"));
//		String ticketNo =  String.valueOf(requestMap.get("TICKET_NO"));

		String processStatus = "";
		String preStatus = "";
		String blockStatus = "";

		Map<String, Object> respMethod = new HashMap<String, Object>();
		Map<String, String> actionInfo = new HashMap<String, String>();
		Map<String, String> respAction = new HashMap<String, String>();

		/**
		 * Get Current Status of the ticket-block
		 */
		if (!ticketBlockId.equals("")) {
			preStatus = misReportBlockRepository.getBlockStatusByTicketBlockId(ticketBlockId);
		}

		/**
		 * Check if Action Code / Sync Id is present with History Table Status
		 */
		if (!syncId.trim().equals("") && !ticketBlockId.trim().equals("")) {

			// Allow Not Affecting Action Codes

			// Add Flag Column

			// ticketBlockId , syncId, actionCode
//			getReportBlockByFilter
		}

		/**
		 * Process Tickets
		 */
		switch (String.valueOf(requestMap.get("actionCode"))) {

//		case "wAptmCusAvailable":
//			checkReportBlock(ticketBlockId, processId);
//			respAction = wAptmCusAvailable(requestMap, ticketBlockId);
//			break;

		case "wAptmFixing":
			checkReportBlock(ticketBlockId, processId);
			respAction = wAptmFixing(requestMap, ticketBlockId);
			break;

		case "wFeAssign":
			checkReportBlock(ticketBlockId, processId);
			respAction = wFeAssign(requestMap, ticketBlockId);
			break;

		// MANOJ CODE
		case "mFeProcessStart":
			checkReportBlock(ticketBlockId, processId);
			respAction = mFeProcessStart(requestMap, ticketBlockId);
			break;

		case "mFeProcessEnd":
			checkReportBlock(ticketBlockId, processId);
			respAction = mFeProcessEnd(requestMap, ticketBlockId);
			break;

		case "wBarcodeBlockAssign":
			checkReportBlock(ticketBlockId, processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = wBarcodeBlockAssign(requestMap, ticketBlockId);
			break;

		case "wBarcodeProdAssign":
			checkReportProduct(ticketProductId, processId);
			respAction = wBarcodeProdAssign(requestMap, ticketProductId);
			break;

		case "wBarcodeTicketAssign":
			checkReportTicket(processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = wBarcodeTicketAssign(requestMap, processId);
			break;
		case "wInventoryBlockAssign":
			checkReportBlock(ticketBlockId, processId);
			respAction = wInventoryBlockAssign(requestMap, ticketBlockId);
			break;
		case "mInventoryBlock":
			checkReportProduct(ticketProductId, processId);
			respAction = mInventoryBlockUsed(requestMap, ticketBlockId);
			break;
		case "wInventoryBlockAddition":
			checkReportBlock(ticketBlockId, processId);
			respAction = wInventoryBlockAddition(requestMap, ticketBlockId);
			break;
		case "wBlockUnusedInventoryReturn":
			checkReportTicket(processId);
			respAction = wBlockUnusedInventoryReturn(requestMap, ticketBlockId);
			break;
		case "wBlockInventoryVerified":
			checkReportTicket(processId);
			respAction = wBlockInventoryVerified(requestMap, ticketBlockId);
			break;
		case "wBlockOldInventoryReturn":
			checkReportTicket(processId);
			respAction = wBlockOldInventoryReturn(requestMap, ticketBlockId);
			break;
		case "wPackingMaterial":
			checkReportTicket(processId);
			respAction = wPackingMaterial(requestMap, ticketBlockId);
			break;

		case "wPickupConfirm":
			checkReportBlock(ticketBlockId, processId);
			respAction = wPickupConfirm(requestMap, ticketBlockId);
			break;

		case "wLinehaul":
			// Not required to check block

			respAction = wLinehaul(requestMap, ticketBlockId);
			break;
		case "wLinehaulVerification":
			// Not required to check block

			respAction = wLinehaulVerification(requestMap, ticketBlockId);
			break;
		case "wDropConfirm":
			checkReportBlock(ticketBlockId, processId);
			respAction = wDropConfirm(requestMap, ticketBlockId);
			break;
		case "wRtoInitiate":
			checkReportBlock(ticketBlockId, processId);
			respAction = wRtoInitiate(requestMap, ticketBlockId);
			break;
		case "wBlockClosed":
			checkReportBlock(ticketBlockId, processId);
			// requestMap.put("syncId", "==============================================Need
			// Changes") ;
			respAction = wBlockClosed(requestMap, ticketBlockId);
			break;

		case "mCustStatus":
			checkReportBlock(ticketBlockId, processId);
			respAction = mCustStatus(requestMap, ticketBlockId);
			break;
		case "mCustDetails":
			checkReportBlock(ticketBlockId, processId);
			respAction = mCustDetails(requestMap, ticketBlockId);
			break;

		case "mPartMissing":
			checkReportBlock(ticketBlockId, processId);
			respAction = mPartMissing(requestMap, ticketBlockId);
			break;

		case "mPickup":
			checkReportBlock(ticketBlockId, processId);
			respAction = mPickup(requestMap, ticketBlockId);
			break;
			
		case "wInvoice":
			checkReportBlock(ticketBlockId, processId);
			respAction = wInvoice(requestMap, ticketBlockId);
			break;
			
		case "mDrop":
			checkReportBlock(ticketBlockId, processId);
			respAction = mDrop(requestMap, ticketBlockId);
			break;
		case "mClosed":
			checkReportBlock(ticketBlockId, processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = mClosed(requestMap, ticketBlockId);
			break;

		case "mRTORequest":
			checkReportBlock(ticketBlockId, processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = mRTORequest(requestMap, ticketBlockId);
			break;

		case "wRTOInitiate":
			checkReportBlock(ticketBlockId, processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = wRTOInitiate(requestMap, ticketBlockId);
			break;

		case "wRTOReject":
			checkReportBlock(ticketBlockId, processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = wRTOReject(requestMap, ticketBlockId);
			break;

		case "mRescheduleRequest":
			checkReportBlock(ticketBlockId, processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = mRescheduleRequest(requestMap, ticketBlockId);
			break;

		case "wRescheduleInitiate":
			checkReportBlock(ticketBlockId, processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = wRescheduleInitiate(requestMap, ticketBlockId);
			break;

		case "wRescheduleReject":
			checkReportBlock(ticketBlockId, processId);
//			requestMap.put("syncId", "==============================================Need Changes") ;
			respAction = wRescheduleReject(requestMap, ticketBlockId);
			break;

		case "mServicePickup":
			checkReportBlock(ticketBlockId, processId);
			respAction = mServicePickup(requestMap, ticketBlockId);
			break;
		case "mServiceDrop":
			checkReportBlock(ticketBlockId, processId);
			respAction = mServiceDrop(requestMap, ticketBlockId);
			break;

		case "mBuyBackStatus":
			checkReportProduct(ticketProductId, processId);
			respAction = mBuyBackStatus(requestMap, ticketBlockId, ticketProductId);
			break;
		case "mReQuote":
			checkReportProduct(ticketProductId, processId);
			respAction = mReQuote(requestMap, ticketBlockId, ticketProductId);
			break;
		case "mBarcodeBlock":
			checkReportBlock(ticketBlockId, processId);
			respAction = mBarcodeBlockUsed(requestMap, ticketBlockId);

			// Store in History Table, Action & Block Status not required
//			respAction = defaultSuccessResp(requestMap, respAction );
			break;
		case "mBarcodeProd":
			checkReportBlock(ticketBlockId, processId);
			respAction = mBarcodeProdUsed(requestMap, ticketProductId);

			// Store in History Table, Action & Block Status not required
//			respAction = defaultSuccessResp(requestMap, respAction );
			break;
		case "mBarcodeTicket":
			checkReportBlock(ticketBlockId, processId);
			respAction = mBarcodeTicketUsed(requestMap, processId);

			// Store in History Table, Action & Block Status not required
//			respAction = defaultSuccessResp(requestMap, respAction );
			break;
		case "wPartReceived":
			checkReportProduct(ticketBlockId, processId);
			respAction = wPartReceived(requestMap, ticketBlockId);
			break;
		case "mAccessoryDetails":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mBasicEval":
			checkReportProduct(ticketProductId, processId);
			// Store Identification No for Adv.Exc
			respAction = mBasicEval(requestMap, ticketProductId);
			break;
		case "mPhysicEval":
			checkReportProduct(ticketProductId, processId);
			respAction = mPhysicEval(requestMap, ticketBlockId, ticketProductId);
			break;
		case "mPhysicEvalResult":
			checkReportProduct(ticketProductId, processId);
			respAction = mPhysicEvalResult(requestMap, ticketBlockId, ticketProductId);
			break;

		case "mIdentificationNo":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mFrntPhoto":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mBackPhoto":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mRightPhoto":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mLeftPhoto":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mTopPhoto":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mBottomPhoto":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mPhotoAftrPackng":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mBfrPackng":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mBeforeInstallPhoto":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mAfterInstallPhoto":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mPhotoId":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;
		case "mSignature":
			checkReportProduct(ticketProductId, processId);
			// Store in History Table, Action & Block Status not required
			respAction = defaultSuccessResp(requestMap, respAction);
			break;

		case "wOPSCancelled":
			checkReportBlock(ticketBlockId, processId);
			respAction = wOPSCancelled(requestMap, ticketBlockId);
			break;

		case "mCustomeBefore":
			checkReportBlock(ticketBlockId, processId);
			respAction = mCustomeBefore(requestMap, ticketBlockId);
			break;

		case "mCustomeAfter":
			checkReportBlock(ticketBlockId, processId);
			respAction = mCustomeAfter(requestMap, ticketBlockId);
			break;

		default:
			respAction.put("processStatus", "NO_ACTION_CODE");
		}

		processStatus = String.valueOf(respAction.get("processStatus"));

		respMethod.put("actionCode", String.valueOf(requestMap.get("actionCode")));
		actionInfo.put("preStatus", preStatus);
		actionInfo.put("blockStatus", String.valueOf(respAction.get("blockStatus")));

		respMethod.put("ticketNo", String.valueOf(requestMap.get("ticketNo")));
		respMethod.put("syncId", String.valueOf(requestMap.get("syncId")));
		respMethod.put("processId", String.valueOf(requestMap.get("processId")));
		respMethod.put("ticketBlockId", String.valueOf(requestMap.get("ticketBlockId")));
		respMethod.put("ticketProductId", String.valueOf(requestMap.get("ticketProductId")));
		if (String.valueOf(respAction.get("blockStatus")).equals("CUSTOMER_ASK_FOR_REQUOTE")) {

			respMethod.put("valueOffered", String.valueOf(respAction.get("valueOffered")));
		}
//		if (String.valueOf(respAction.get("blockStatus")).equals("PHYSICAL_EVALUATION_RESULT")) {
//
//			respMethod.put("responseStatus", String.valueOf(respAction.get("responseStatus")));
//		}
		actionInfo.put("processStatus", processStatus);
		respMethod.put("processStatus", processStatus);

		if (processStatus.equals("NO_ACTION_CODE")) {
			respMethod.put("methodStatus", "failed");
			respMethod.put("methodMsg",
					ResponseMessage.getMsgType("actionType") + String.valueOf(requestMap.get("actionCode")));
			respMethod.put("actionInfo", actionInfo);

			return respMethod;
		}

		if (processStatus.equals("") || processStatus.equals("success")) {
			respMethod.put("methodStatus", "success");
			respMethod.put("methodMsg", ResponseMessage.UPDATE + " Mis report");
			respMethod.put("actionInfo", actionInfo);

			return respMethod;

		} else {
			respMethod.put("methodStatus", "failed");
			respMethod.put("methodMsg", String.valueOf(respAction.get("actionMsg")));
			respMethod.put("actionInfo", actionInfo);

			return respMethod;
		}

	}

	@SuppressWarnings("unchecked")
	private void misHistoryReport(Map<String, Object> reqJson, String preStatus, String historyBlockStatus) {

		MisReportHistory historyReport = new MisReportHistory();
		String syncId = String.valueOf(reqJson.get("syncId"));

		try {

			historyReport.setEdgeSyncId(String.valueOf(reqJson.get("syncId")));
			if (syncId.equals("")) {
				syncId = IdGenerate.getKey("hilog");
			} else {
				syncId = IdGenerate.getKey(syncId);
			}

			historyReport.setSyncId(syncId);
			historyReport.setProcessId(String.valueOf(reqJson.get("processId")));
			historyReport.setTicketProductId(String.valueOf(reqJson.get("ticketProductId")));
			historyReport.setActionCode(String.valueOf(reqJson.get("actionCode")));
			historyReport.setAction(String.valueOf(reqJson.get("action")));
			historyReport.setIsHistroyRecord(String.valueOf(reqJson.get("isHistoryRecord")));
			historyReport.setIsHistroyShown(String.valueOf(reqJson.get("isHistoryShown")));
			historyReport.setIsStrictLevel(String.valueOf(reqJson.get("isStrictLevel")));
			historyReport.setSyncStatus(String.valueOf(reqJson.get("syncStatus")));
			historyReport.setTicketBlockId(String.valueOf(reqJson.get("ticketBlockId")));

			historyReport.setBlockStatus(historyBlockStatus);
			historyReport.setPreStatus(preStatus);

			try {

				historyReport.setResultSet(String.valueOf(new JSONObject(reqJson)));
			} catch (Exception e) {
				historyReport.setResultSet(String.valueOf(reqJson));
				e.printStackTrace();
			}

			/*
			 * inserting ACTION_SET if actionCode is "wBlockUnusedInventoryReturn" is there
			 * in resultset
			 */

			Map<String, Object> resultSet = (Map<String, Object>) (reqJson.get("resultSet"));

			String actionCode = String.valueOf(reqJson.get("actionCode"));
			if (actionCode.equals("wBlockUnusedInventoryReturn")) {
				try {

					historyReport.setActionSet(String.valueOf(new JSONObject(resultSet)));
				} catch (Exception e) {
					historyReport.setActionSet(String.valueOf(resultSet));
					e.printStackTrace();
				}
			}

			Map<String, Object> resultSetMap = (Map<String, Object>) reqJson.get("resultSet");

			historyReport.setMisStatus(String.valueOf(resultSetMap.get("actionStatus")));
			setObserver(historyReport);

			if (!(String.valueOf(resultSetMap.get("processAt")).equals("null"))) {
				historyReport.setProcessAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
						.parse(String.valueOf(resultSetMap.get("processAt"))));
			}

			historyReport.setBlockStatus(historyBlockStatus);

			misHistoryRepo.save(historyReport);

		} catch (Exception e) {

			try {
				historyReport.setProcessAt(null);
				misHistoryRepo.save(historyReport);
			} catch (Exception e1) {
				try {
					this.logAPIError(reqJson);
				} catch (Exception e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				e1.printStackTrace();
			}

			// e.printStackTrace();

		}

	}

	@SuppressWarnings({ "unchecked", "deprecation" })
	private MisReportHistoryLogs misHistoryReportLogs(Map<String, Object> reqJson, String preStatus,
			String historyBlockStatus) {

		MisReportHistoryLogs historyReportLogs = new MisReportHistoryLogs();
		String syncId = String.valueOf(reqJson.get("syncId"));

		try {

			historyReportLogs.setEdgeSyncId(String.valueOf(reqJson.get("syncId")));
			if (syncId.equals("")) {
				syncId = IdGenerate.getKey("hilog");
			} else {
				syncId = IdGenerate.getKey(syncId);
			}

			historyReportLogs.setSyncId(syncId);

			historyReportLogs.setProcessId(String.valueOf(reqJson.get("processId")));
			historyReportLogs.setTicketProductId(String.valueOf(reqJson.get("ticketProductId")));
			historyReportLogs.setActionCode(String.valueOf(reqJson.get("actionCode")));
			historyReportLogs.setAction(String.valueOf(reqJson.get("action")));
			historyReportLogs.setIsHistroyRecord(String.valueOf(reqJson.get("isHistoryRecord")));
			historyReportLogs.setIsHistroyShown(String.valueOf(reqJson.get("isHistoryShown")));
			historyReportLogs.setIsStrictLevel(String.valueOf(reqJson.get("isStrictLevel")));
			historyReportLogs.setSyncStatus(String.valueOf(reqJson.get("syncStatus")));
			historyReportLogs.setTicketBlockId(String.valueOf(reqJson.get("ticketBlockId")));

			historyReportLogs.setBlockStatus(historyBlockStatus);
			historyReportLogs.setPreStatus(preStatus);

//			Object json;
//			json = reqJson;
//			JSONObject json1 = new JSONObject();
//			json1.put("payload", json);
//			historyReportLogs.setResultSet(String.valueOf(json1));

			try {

				historyReportLogs.setResultSet(String.valueOf(new JSONObject(reqJson)));

			} catch (Exception e) {
				historyReportLogs.setResultSet(String.valueOf(reqJson));
				e.printStackTrace();
			}

			Map<String, Object> resultSetMap = (Map<String, Object>) reqJson.get("resultSet");
			historyReportLogs.setMisStatus(String.valueOf(resultSetMap.get("actionStatus")));
			setObserver(historyReportLogs);

			if (!(String.valueOf(resultSetMap.get("processAt")).equals("null"))) {

				historyReportLogs.setProcessAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
						.parse(String.valueOf(resultSetMap.get("processAt"))));
			}

			misHistoryLogsRepo.save(historyReportLogs);

		} catch (Exception e) {

			try {

				historyReportLogs.setProcessAt(null);
				misHistoryLogsRepo.save(historyReportLogs);
			} catch (Exception e1) {
				try {
					this.logAPIError(reqJson);
				} catch (Exception e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
				e1.printStackTrace();
			}

			// e.printStackTrace();
		}
		return historyReportLogs;
	}

	private void logAPIError(Map<String, Object> reqJson) {

		try {

			JSONObject logData = new JSONObject();
			logData.put("payload", String.valueOf(reqJson));
			this.callApiPut(MOB_LOGS_URL + "API_" + this.getValidUserName(), logData);
		} catch (Exception e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

	}

	private String checkReportTicket(String processId) {

		if (processId != "" && misReportTicketsRepository.existOrNot(processId) == 0) {
			misReportServices.storeMisData(Map.of("processId", processId));
		}
		return "success";
	}

	private String checkReportBlock(String ticketBlockId, String processId) {
		if (misReportBlockRepository.existOrNot(ticketBlockId) == 0) {
			misReportServices.storeMisData(Map.of("processId", processId));
		}
		return "success";

	}

	private String checkReportProduct(String ticketProductId, String processId) {

		if (misReportProdRepository.existOrNotByTicketProductId(ticketProductId) == 0) {

			misReportServices.storeMisData(Map.of("processId", processId));
		}

		return "success";
	}

	/** Cancel Ticket Blocks by Ticket Number **/
	public Response canelTicketByTicketNo(String ticketNo) {

		Map<String, String> primaryDetails = misTicketRepo.getPrimaryDetailsByTicketNo(ticketNo);

		if (primaryDetails.isEmpty()) {
			response.respFail(null, ResponseMessage.NO_DATA_FOUND + ticketNo);
			return response;
		}

		// Validation allow only BLOCK_CREATED Status

		String processId = primaryDetails.get("processId");
		ArrayList<String> blocksArray = new ArrayList<String>();

		List<Map<String, Object>> blockList = misBlockRepo.getBlockDetailsStatus(processId);

		// Validation
		for (Map<String, Object> blockRow : blockList) {

			if (String.valueOf(blockRow.get("blockStatus")).equals("API_CANCEL")) {
				response.respOk(ticketNo, "Ticket is already cancelled");
				return response;
			}

			if (!String.valueOf(blockRow.get("blockStatus")).equals("BLOCK_CREATED")) {
				response.respFail(ticketNo, "Ticket is already in Process, cannot be cancelled");
				return response;
			}

			blocksArray.add(String.valueOf(blockRow.get("ticketBlockId")));

		}

		// Passing one by one
		for (int i = 0; i < blocksArray.size(); i++) {

			misBlockRepo.updateBlockStatusAndActionStatus(blocksArray.get(i));

			// Insert into History Table
			MisReportHistory misReportHistory = new MisReportHistory();
			misReportHistory.setSyncId(this.generateProcessIdKey(ticketNo));
			misReportHistory.setProcessId(processId);
			misReportHistory.setAction("API_CANCEL");
			misReportHistory.setActionCode("apiCancel");
			misReportHistory.setSyncStatus("SYNC");
			misReportHistory.setMisStatus("API_CANCELLED");
			misReportHistory.setBlockStatus("API_CANCELLED");
			misReportHistory.setResultSet("{actionStatus=Cancelled}");
			misReportHistory.setTicketBlockId(blocksArray.get(i));
			misReportHistory.setTicketProductId("");
			misReportHistory.setIsHistroyRecord("");
			misReportHistory.setIsHistroyShown("");
			misReportHistory.setIsStrictLevel("");

			// Capture Username by Token
			setObserver(misReportHistory);

			// Save into misHistory
			misHistoryRepo.save(misReportHistory);

			// Delete MIS Tables
			misBlockRepo.deleteMisReportProductByTicketBlockId(blocksArray.get(i));
			misBlockRepo.deleteMisReportBlockByTicketBlockId(blocksArray.get(i));
			misBlockRepo.deleteMisReportTicketByTicketNo(ticketNo);

			// Delete MIS Tables
//			misBlockRepo.deleteMisReportTicketByTicketNo(ticketNo);
//			misBlockRepo.deleteMisReportBlockByTicketBlockId(blocksArray.get(i));
//			misBlockRepo.deleteMisReportProductByTicketBlockId(blocksArray.get(i));

		}

		response.respOk(ticketNo, "Ticket is cancel by the retailer by API");
		return response;
	}

	/*
	 * private void updateTicketProcessBlocks(String syncStatus, String blockStatus,
	 * String actionStatus, String ticketBlockId) {
	 * misReportBlockRepository.updateTicketProcessBlocksSyncBlockAndAction(
	 * syncStatus, blockStatus, actionStatus, ticketBlockId); }
	 * 
	 * private MisReportBlock getById(String misBlockId) {
	 * 
	 * Optional<MisReportBlock> misBlockReport =
	 * misReportBlockRepository.findById(misBlockId);
	 * 
	 * return misBlockReport.orElseThrow(() -> new
	 * RuntimeException(ResponseMessage.NO_DATA_FOUND + misBlockId)); }
	 * 
	 * @SuppressWarnings("unchecked") private void
	 * updateProductMisReport(Map<String, Object> requestMap, String actionType) {
	 * 
	 * String processId = String.valueOf(requestMap.get("processId")); String
	 * ticketBlockId = String.valueOf(requestMap.get("ticketBlockId")); Map<String,
	 * Object> resultSet = (Map<String, Object>) requestMap.get("resultSet");
	 * 
	 * MisReportProduct misReportProduct =
	 * misReportProdRepository.findByTicketBlockIdAndProcessId(ticketBlockId,
	 * processId);
	 * 
	 * 
	 * 
	 * switch (actionType) { case "mPickup":
	 * misReportProduct.setTicketBlockIdPickup(String.valueOf(resultSet.get(
	 * "products")));
	 * 
	 * updateTicketProcessBlocks(String.valueOf(requestMap.get("syncStatus")),
	 * String.valueOf(resultSet.get("actionStatus")),
	 * String.valueOf(resultSet.get("actionStatus")), ticketBlockId); break; case
	 * "mDrop":
	 * misReportProduct.setTicketBlockIdDrop(String.valueOf(resultSet.get("products"
	 * ))); updateTicketProcessBlocks(String.valueOf(requestMap.get("syncStatus")),
	 * String.valueOf(resultSet.get("actionStatus")),
	 * String.valueOf(resultSet.get("actionStatus")), ticketBlockId); break; case
	 * "mClosed":
	 * misReportProduct.setTicketBlockIdClosed(String.valueOf(resultSet.get(
	 * "products")));
	 * updateTicketProcessBlocks(String.valueOf(requestMap.get("syncStatus")),
	 * String.valueOf(resultSet.get("actionStatus")),
	 * String.valueOf(resultSet.get("actionStatus")), ticketBlockId); break;
	 * default: throw new RuntimeException(ResponseMessage.NO_DATA_FOUND +
	 * actionType); }
	 * 
	 * }
	 */

	public <T> List<T> jsonArrayToListOfObj(String jsonArray, Class<T> type) {
		Gson gson = new Gson();
		return gson.fromJson(jsonArray, new TypeToken<List<Object>>() {
		}.getType());
	}

	/* mBarcodeBlock,mBarcodeTicket and mBarcodeProd */

	@SuppressWarnings("unchecked")
	private Map<String, String> mBarcodeTicketUsed(Map<String, Object> requestMap, String processId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = "";
			String processAt = String.valueOf(resultSet.get("processAt"));

			String inputTicketBarcode = String.valueOf(resultSet.get("textBarcode"));

			// Update Status Based on Barcode
			updateBarcodeStatus(inputTicketBarcode, "20used");

			// Update Ticket Product Id in Barcodes Table Based on Barcode
			misReportBlockRepository.updateProcessIdBasedOnBarcode(inputTicketBarcode, processId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mBarcodeBlockUsed(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = "";
			String processAt = String.valueOf(resultSet.get("processAt"));

			String inputTicketBarcode = String.valueOf(resultSet.get("textBarcode"));

			// Update Status Based on Barcode
			updateBarcodeStatus(inputTicketBarcode, "20used");

			// Update Ticket Product Id in Barcodes Table Based on Barcode
			misReportBlockRepository.updateTicketBlockIdBasedOnBarcode(inputTicketBarcode, ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mBarcodeProdUsed(Map<String, Object> requestMap, String ticketProductId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = "";
			String processAt = String.valueOf(resultSet.get("processAt"));

			String inputTicketBarcode = String.valueOf(resultSet.get("textBarcode"));

			// Update Status Based on Barcode
			updateBarcodeStatus(inputTicketBarcode, "20used");

			// Update Ticket Product Id in Barcodes Table Based on Barcode
			misReportBlockRepository.updateTicketProductIdBasedOnBarcode(inputTicketBarcode, ticketProductId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;

	}

	// RESCHEDULE MODERATION

	@SuppressWarnings("unchecked")
	private Map<String, String> mRescheduleRequest(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			Integer count;

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));
//			String flowStatus = "DROP_WEB";

			if (processAt.equals("null")) {
				processAt = getDateAndTime();
			}

			String blockStatus = "";

			switch (actionStatus) {
			case "RESCHEDULE_REQUEST_FE_AT_PICKUP":
				blockStatus = "RESCHEDULE_REQUEST_FE_AT_PICKUP";
				flowStatus = "PICK_WEB";
				break;
			case "RESCHEDULE_REQUEST_FE_AT_DROP":
				blockStatus = "RESCHEDULE_REQUEST_FE_AT_DROP";
				flowStatus = "DROP_WEB";
				break;
			default:
				blockStatus = actionStatus;
			}

			if (actionStatus.equals("RESCHEDULE_REQUEST_FE_AT_PICKUP")
					|| actionStatus.equals("RESCHEDULE_REQUEST_FE_AT_DROP")) {
				count = misReportBlockRepository.getCount(ticketBlockId);
				count = count + 1;

				String remark = String.valueOf(resultSet.get("reScheduleDateTime")) + " | "
						+ String.valueOf(resultSet.get("remark"));

				switch (count) {

				case 1:
					misReportBlockRepository.updateAttempt1(ticketBlockId, remark, processAt, count);
					break;
				case 2:
					misReportBlockRepository.updateAttempt2(ticketBlockId, remark, processAt, count);
					break;
				case 3:
					misReportBlockRepository.updateAttempt3(ticketBlockId, remark, processAt, count);
					break;
				case 4:
					misReportBlockRepository.updateAttempt4(ticketBlockId, remark, processAt, count);
					break;
				default:
					misReportBlockRepository.updateAttempt5(ticketBlockId, remark, processAt, count);
					break;
				}

			}

			// Update Reschedule Date and Time

			// mRescheduleRequest
			misReportBlockRepository.updateBlockMISWAptmFixing(String.valueOf(resultSet.get("reScheduleDateTime")),
					ticketBlockId);
			misReportBlockRepository.updateOpsWAptmFixing(String.valueOf(resultSet.get("reScheduleDateTime")),
					ticketBlockId);

			// Update Direction to RTO in ops & mis

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, ticketBlockId,
					getDateAndTime());
			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, actionStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wRescheduleInitiate(Map<String, Object> requestMap, String ticketBlockId)
			throws ClientProtocolException, IOException, JSONException {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));
//			String flowStatus = "DROP_WEB";

//			String srcPincode = String.valueOf(resultSet.get("srcPincode"));
//			String dstPincode = String.valueOf(resultSet.get("dstPincode"));
//			String flowId = String.valueOf(resultSet.get("flowId"));

			String blockStatus = "";

			switch (actionStatus) {
			case "RESCHEDULE_FE_AT_PICKUP":
				blockStatus = "RESCHEDULE_FE_AT_PICKUP";
				flowStatus = "PICK_WEB";
				break;
			case "RESCHEDULE_FE_AT_DROP":
				blockStatus = "RESCHEDULE_FE_AT_DROP";
				flowStatus = "DROP_WEB";
				break;
			case "RESCHEDULE_FE_SERVICE_AT_PICKUP":
				blockStatus = "RESCHEDULE_FE_SERVICE_AT_PICKUP";
				flowStatus = "PICK_WEB";
				break;
			case "RESCHEDULE_FE_SERVICE_AT_DROP":
				blockStatus = "RESCHEDULE_FE_SERVICE_AT_DROP";
				flowStatus = "DROP_WEB";
				break;
			default:
				blockStatus = actionStatus;
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());
			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wRescheduleReject(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));

//			String flowStatus = "DROP_WEB";

			String blockStatus = "";

			switch (actionStatus) {
			case "ASSIGNED_FE_FOR_PICKUP":
				blockStatus = "ASSIGNED_FE_FOR_PICKUP";
				flowStatus = "PICK_MOB";
				break;
			case "ASSIGNED_FE_FOR_SERVICE_PICKUP":
				blockStatus = "ASSIGNED_FE_FOR_SERVICE_PICKUP";
				flowStatus = "PICK_MOB";
				break;
			case "ASSIGNED_FE_FOR_SERVICE_DROP":
				blockStatus = "ASSIGNED_FE_FOR_SERVICE_DROP";
				flowStatus = "DROP_MOB";
				break;
			case "ASSIGNED_FE_FOR_DROP":
				blockStatus = "ASSIGNED_FE_FOR_DROP";
				flowStatus = "DROP_MOB";
				break;
			default:
				blockStatus = actionStatus;
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(actionStatus, actionStatus, ticketBlockId,
					getDateAndTime());
			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, actionStatus, actionStatus,
					ticketBlockId);
			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> wOPSCancelled(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));
			String remarkMsg = String.valueOf(resultSet.get("remarks"));

//			String flowStatus = "DROP_WEB";

			String blockStatus = "";

			switch (actionStatus) {
			case "OPS_CANCALLATION":
				blockStatus = "OPS_CANCEL";
				break;
			default:
				blockStatus = actionStatus;
			}

			System.out.println("BLOCK STATUS------------->" + blockStatus);

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());

			// OPS
			misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
					ticketBlockId);

			// INSERT INTO PROCESS REMARKS
			this.insertTicketBlockRemarks(ticketBlockId, remarkMsg);

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	// MOHAN CODE FOR REPEATATION BUG FIXED

	@SuppressWarnings("unchecked")
	private String validateProcessdAt(Map<String, Object> requestMap) throws ParseException {

		Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

		String processAt = String.valueOf(resultSet.get("processAt"));

		if (processAt.equals("null") || processAt.isEmpty() || processAt.isBlank()) {
			System.out.println("<==================processAt null===================================>");
			return "success";
		}

		String ticketBlockId = String.valueOf(requestMap.get("ticketBlockId"));

		System.out.println(processAt + "<=======****************************processAtFromJSON/");

		Map<String, Object> latestProcessAt = misReportBlockRepository.getLatestProcessAtByticketBlockId(ticketBlockId);

		String dbProcessAt = String.valueOf(latestProcessAt.get("processAt"));
		if (dbProcessAt.equals("null") || dbProcessAt.isEmpty() || dbProcessAt.isBlank()) {
			System.out.println("<==================processAt null===================================>");
			return "success";
		}
		System.out.println(dbProcessAt + "<=======*****************************processAtFromDB");

		String newprocessAt = processAt.replace("T", " ");
		System.out.println(newprocessAt + "newString...........................");

		// 2022-11-16 14:40:22
		// String str = "2022-11-16 14:40:22";//2022-11-16 14:40:22.0

		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		Date date1 = df.parse(newprocessAt);
		long epochJson = date1.getTime();

		Date date2 = df.parse(dbProcessAt);
		long epochDb = date2.getTime();

		System.out.println(
				epochJson + "epochJson<======================" + epochDb + "epochDb<=========================");

		if (epochJson >= epochDb) {
			System.out.println("====================================================");
			return "success";
		}

		// 2022-11-16T14:40:22 -> JSON
		// 2022-11-16 14:40:22.0 -> DB

		return "unsuccess";

	}

	private Map<String, Object> updateResponseIfBlockExists(Map<String, Object> requestMap) {

		// TODO Auto-generated method stub
		Map<String, Object> reqBody = new LinkedHashMap<>();
		Map<String, Object> actionInfo = new LinkedHashMap<>();
		reqBody.put("ticketNo", String.valueOf(requestMap.get("ticketNo")));
		reqBody.put("ticketBlockId", String.valueOf(requestMap.get("ticketBlockId")));
		reqBody.put("processId", String.valueOf(requestMap.get("processId")));
		reqBody.put("actionCode", String.valueOf(requestMap.get("actionCode")));
		reqBody.put("ticketProductId", String.valueOf(requestMap.get("ticketProductId")));
		reqBody.put("syncId", String.valueOf(requestMap.get("syncId")));
		reqBody.put("processStatus", "success");
		reqBody.put("methodStatus", "success");
		reqBody.put("methodMsg", "processedAt is old");
		reqBody.put("actionInfo", actionInfo);
		reqBody.put("valueOffered", String.valueOf(requestMap.get("valueOffered")));

		return reqBody;
	}

	// DORE CODE FOR CUSTOM EVALUATION

	@SuppressWarnings("unchecked")
	private Map<String, String> mCustomeBefore(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			System.out.println("actionStatus*********>" + actionStatus);
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));

			String blockStatus = "";

			switch (actionStatus) {
			case "CUSTOMETDS_BEFORE":
				blockStatus = "CUSTOMETDS_BEFORE";
				break;
			default:
				blockStatus = actionStatus;
			}

			List<Map> list = (List<Map>) resultSet.get("customeFields");

			for (int i = 0; i < list.size(); i++) {
				Map<String, String> tds = list.get(i);

				String field = String.valueOf(tds.get("field"));
				String tdsBefore = String.valueOf(tds.get("tdsBefore"));

//				todo update method

				switch (field) {

				case "cusField07":
					misReportBlockRepository.updateCusField7(ticketBlockId, tdsBefore);
					break;
				}

			}
			System.out.println("BLOCK STATUS------------->" + blockStatus);

//				// MIS
//				misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
//						getDateAndTime());
			//
//				// OPS
//				misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
//						ticketBlockId);

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	@SuppressWarnings("unchecked")
	private Map<String, String> mCustomeAfter(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));

//				String flowStatus = "DROP_WEB";

			String blockStatus = "";

			switch (actionStatus) {
			case "CUSTOMETDS_AFTER":
				blockStatus = "CUSTOMETDS_AFTER";
				break;
			default:
				blockStatus = actionStatus;
			}

			List<Map> list = (List<Map>) resultSet.get("customeFields");

			for (int i = 0; i < list.size(); i++) {
				Map<String, String> tds = list.get(i);

				String field = String.valueOf(tds.get("field"));
				String tdsAfter = String.valueOf(tds.get("tdsAfter"));

//				todo update method

				switch (field) {

				case "cusField08":
					misReportBlockRepository.updateCusField8(ticketBlockId, tdsAfter);
					break;
				case "cusField09":
					misReportBlockRepository.updateCusField9(ticketBlockId, tdsAfter);
					break;
				}

			}

			System.out.println("BLOCK STATUS------------->" + blockStatus);

//				// MIS
//				misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
//						getDateAndTime());
			//
//				// OPS
//				misReportBlockRepository.updateBlockOpsBlockActionStatus(flowStatus, blockStatus, actionStatus,
//						ticketBlockId);

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	//MOHAN CODE FOR INVENTORY

		// inventory
		@SuppressWarnings("unchecked")
		private Map<String, String> wInventoryBlockAssign(Map<String, Object> requestMap, String ticketBlockId) {
			Map<String, String> respAction = new HashMap<String, String>();
			try {
				Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
				String processAt = String.valueOf(resultSet.get("processAt"));
				String actionStatus = String.valueOf(resultSet.get("actionStatus"));

				/*
				 * for loop starts here for validation/ If validation goes wrong it won't let
				 * the code to insert status
				 */
				/* VALIDATING THE INVENTORY FOR INSERTING */
				String statusUpdate = this.getBlockInventoryAssign(requestMap, ticketBlockId);

				if (statusUpdate == "NOT_AVAILABLE") {
					System.out.println("===============================================INVENTORY IS ALREADY VERIFIED");
					respAction.put("processStatus", "failed");
					respAction.put("actionMsg", "INVENTORY IS ALREADY VERIFIED");
					return respAction;
				}

				List<Map> list = (List<Map>) resultSet.get("jobInventory");

				for (int i = 0; i < list.size(); i++) {
					Map<String, String> inv = list.get(i);

					String qnt = String.valueOf(inv.get("qnt"));
					String itemId = String.valueOf(inv.get("itemId"));
					String storeBaysId = String.valueOf(inv.get("storeBaysId"));

					System.out.println("===================storeBaysId============================" + storeBaysId);

					/* Check the Inventory Availability Status // returns if stock is not there */
					String inventoryStatus = this.checkInventoryStatus(itemId, storeBaysId, qnt);

					if (!inventoryStatus.equals("available")) {
						System.out.println("===============================================NOT AVAILABLE");
						respAction.put("processStatus", "failed");
						respAction.put("actionMsg", inventoryStatus);
						return respAction;
					}
				}
				System.out.println("===============================================AVAILABLE");

				/*
				 * HERE IT WILL UPDATE THE TRANSACTION AND UPDATE THE COUNT IN THE INVENTORY
				 * DATABASE TABLES
				 */
				for (int i = 0; i < list.size(); i++) {
					System.out.println("===============================================AVAILABLE ******************");
					Map<String, String> inv = list.get(i);

					String qnt = String.valueOf(inv.get("qnt"));
					String itemId = String.valueOf(inv.get("itemId"));
					String storeBaysId = String.valueOf(inv.get("storeBaysId"));

					// insert transaction in the "inv_trx_stores_" table that ticket has allocated
					// with this qnt & update inv_items_quantity_ "qnt"

					String trxType = "debit";
					this.insertTrxToInvTrxStores(requestMap, qnt, itemId, storeBaysId, trxType);

					// update inv_items_quantity_ count
					misReportBlockRepository.updateInvItemsQuatity(itemId, storeBaysId, qnt);
				}

				/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY column */

				misReportBlockRepository.updateInventoryBlockTable(ticketBlockId,
						String.valueOf(new JSONObject(resultSet)));

				// update to ticket_process_blocks
				misReportBlockRepository.updateTicketProcessBlocks(ticketBlockId,
						String.valueOf(new JSONObject(resultSet)));

//					// mis
//					misReportBlockRepository.updateBlockMISActionStatus(actionStatus, ticketBlockId);
//			
//					// Ops
//					misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

				respAction.put("processStatus", "success");
				respAction.put("actionStatus", actionStatus);
				respAction.put("blockStatus", actionStatus);
				respAction.put("processAt", processAt);
				respAction.put("actionMsg", "Action Message");
			} catch (Exception e) {
				respAction.put("processStatus", "failed");
				e.printStackTrace();
			}
			// TODO Auto-generated method stub
			return respAction;
		}

		private String checkInventoryStatus(String itemId, String storeBaysId, String qnt) {

			System.out.println(itemId + storeBaysId + "<====================================VALUES CHECK");
			Integer inventoryCount = misReportBlockRepository.getInventoryCount(itemId, storeBaysId);
			System.out.println(inventoryCount + "<======================================");
			// String count = String.valueOf(inventoryCount.get("qnt"));
			int dbCount = inventoryCount;
			int assignQnt = Integer.parseInt(qnt);

			if (assignQnt <= dbCount) {
				System.out.println("================>AVAILABLE");
				return "available";
			} else {
				return "stock is not available";
			}

		}

		@SuppressWarnings("unchecked")
		private void insertTrxToInvTrxStores(Map<String, Object> requestMap, String qnt, String itemId, String storeBaysId,
				String trxType) {
			InventoryTrxStores status = new InventoryTrxStores();

			String ticketProductId = String.valueOf(requestMap.get("ticketProductId"));

			if (!(ticketProductId == null || ticketProductId.isEmpty() || ticketProductId.isBlank())) {
				status.setRefProductId(ticketProductId);
			}
			String ticketNo = String.valueOf(requestMap.get("ticketNo"));
			String storingId = IdGenerate.getKey(ticketNo);
			System.out.println(storingId + "<==================================");
			float qntf = Float.parseFloat(qnt);

			status.setStoringId(storingId);
			status.setItemId(itemId);
			status.setStoreBaysId(storeBaysId);
			status.setTrxType(trxType);
			status.setQnt(qntf);
			status.setRefJobId(String.valueOf(requestMap.get("ticketBlockId")));
			status.setRefprocessId(String.valueOf(requestMap.get("processId")));
			status.setRefTicketNo(ticketNo);
			status.setHandledAt(getDateAndTime());
			status.setHandledBy(getValidUser());
			status.setCreatedAt(getDateAndTime());
			status.setCreatedBy(getValidUser());

			inventoryTrxStoresRepository.save(status);

		}

		@SuppressWarnings("unchecked")
		private Map<String, String> mInventoryBlockUsed(Map<String, Object> requestMap, String ticketBlockId) {
			Map<String, String> respAction = new HashMap<String, String>();
			try {
				Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

				String actionStatus = String.valueOf(resultSet.get("actionStatus"));
				// String blockStatus = "";
				String processAt = String.valueOf(resultSet.get("processAt"));

				/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY_FE column */
				misReportBlockRepository.updateInventoryBlockTableFe(ticketBlockId,
						String.valueOf(new JSONObject(resultSet)));

				/* update to ticket_process_blocks BLOCK_INVENTORY_FE column */
				misReportBlockRepository.updateTicketProcessBlocksFe(ticketBlockId,
						String.valueOf(String.valueOf(new JSONObject(resultSet))));

//					// mis
//					misReportBlockRepository.updateBlockMISActionStatus(actionStatus, ticketBlockId);
//						
//					// Ops
//					misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

				respAction.put("processStatus", "success");
				respAction.put("actionStatus", actionStatus);
				respAction.put("blockStatus", actionStatus);
				respAction.put("processAt", processAt);
				respAction.put("actionMsg", "Action Message");

			} catch (Exception e) {
				respAction.put("processStatus", "failed");
				e.printStackTrace();
			}
			// TODO Auto-generated method stub
			return respAction;
		}

		@SuppressWarnings("unchecked")
		private Map<String, String> wBlockOldInventoryReturn(Map<String, Object> requestMap, String ticketBlockId)
				throws JSONException {
			Map<String, String> respAction = new HashMap<String, String>();
			try {

				Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

				String actionStatus = String.valueOf(resultSet.get("actionStatus"));
				String processAt = String.valueOf(resultSet.get("processAt"));

				List<Map> list = (List<Map>) resultSet.get("jobOldInventory");

				/* validating inputs */
				for (int i = 0; i < list.size(); i++) {
					Map<String, String> inv = list.get(i);
					String qnt = String.valueOf(inv.get("qnt"));
					System.out.println("=======================QUANTITY============>" + qnt);
					int qntNumber = Integer.parseInt(qnt);

					if (qnt == null || qnt.isEmpty() || qnt.isBlank() || qntNumber == 0) {
						System.out.println("===============================================QNT IS NOT THERE");
						respAction.put("processStatus", "failed");
						respAction.put("actionMsg", "quantity should not be null or 0");
						return respAction;
					}
				}
				/*
				 * HERE IT WILL UPDATE THE TRANSACTION AND UPDATE THE COUNT IN THE INVENTORY
				 * DATABASE TABLES
				 */
				for (int i = 0; i < list.size(); i++) {
					System.out.println("===============================================>AVAILABLE");
					Map<String, String> inv = list.get(i);

					String qnt = String.valueOf(inv.get("qnt"));
					System.out.println("=======================QUANTITY============>" + qnt);
					String itemId = String.valueOf(inv.get("itemId"));
					String storeBaysId = String.valueOf(inv.get("storeBaysId"));

					// insert transaction in the "inv_trx_stores_" table that ticket has allocated
					// with this qnt & update inv_items_quantity_ "qnt"

					String trxType = "credit";
					this.insertTrxToInvTrxStores(requestMap, qnt, itemId, storeBaysId, trxType);

					Integer count = misReportBlockRepository.getInvItemsQuatity(itemId, storeBaysId);

					if (count <= 0) {
						this.insertDataItemInvQnt(itemId, storeBaysId, qnt);
						System.out.println("=====>CREATING NEW ROW IN INVQNT<======");
					} else {
						System.out.println("=====>UPDATING<======");
						// update inv_items_quantity_ count (add old items to database)
						misReportBlockRepository.updateOldInvItemsQuatity(itemId, storeBaysId, qnt);
					}
				}

				/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY_RETURNS column */
				misReportBlockRepository.updateInventoryBlockTableReturns(ticketBlockId,
						String.valueOf(new JSONObject(resultSet)));

				/* update to ticket_process_blocks BLOCK_INVENTORY_RETURNS column */
				misReportBlockRepository.updateTicketProcessBlocksReturns(ticketBlockId,
						String.valueOf(new JSONObject(resultSet)));

//					// mis
//					misReportBlockRepository.updateBlockMISActionStatus(actionStatus, ticketBlockId);
//						
//					// Ops
//					misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
				respAction.put("processStatus", "success");
				respAction.put("actionStatus", actionStatus);
				respAction.put("blockStatus", actionStatus);
				respAction.put("processAt", processAt);
				respAction.put("actionMsg", "Action Message");

			} catch (Exception e) {
				respAction.put("processStatus", "failed");
				e.printStackTrace();
			}
			// TODO Auto-generated method stub
			return respAction;
		}
		
		private String insertDataItemInvQnt(String itemId, String storeBaysId, String qnt) {
			try {
				misReportBlockRepository.insertInvQntItems(storeBaysId, itemId, qnt, getDateAndTime(), getValidUserName(), getDateAndTime(), getValidUserName());
				return "updated";
			} catch (Exception e) {
				e.printStackTrace();
				return "notUpdated";
			}
		}

		@SuppressWarnings("unchecked")
		private Map<String, String> wBlockInventoryVerified(Map<String, Object> requestMap, String ticketBlockId) {
			Map<String, String> respAction = new HashMap<String, String>();
			try {

				Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

				String actionStatus = String.valueOf(resultSet.get("actionStatus"));
				String processAt = String.valueOf(resultSet.get("processAt"));

				/* VALIDATING THE INVENTORY FOR INSERTING */
				String statusUpdate = this.getBlockInventoryVerified(requestMap, ticketBlockId);

				if (statusUpdate == "NOT_AVAILABLE") {
					System.out.println("===============================================INVENTORY IS ALREADY VERIFIED");
					respAction.put("processStatus", "failed");
					respAction.put("actionMsg", "INVENTORY IS ALREADY VERIFIED");
					return respAction;
				}

				List<Map> list = (List<Map>) resultSet.get("jobVerifiedInventory");
				//VERIFICATION
				for (int i = 0; i < list.size(); i++) {
					System.out.println("========>VEIFICATION<=======");
					Map<String, String> inv = list.get(i);
					
					String qnt = String.valueOf(inv.get("qnt"));
					String feQnt = String.valueOf(inv.get("feQnt"));
					String verifiedQnt = String.valueOf(inv.get("verifiedQnt"));
					
					int qntNumber = Integer.parseInt(qnt);
					int feQntNumber = Integer.parseInt(feQnt);
					int verifiedQntNumber = Integer.parseInt(verifiedQnt);
					
					if(verifiedQntNumber > qntNumber){
						System.out.println("===============================================INVENTORY IS ALREADY VERIFIED");
						respAction.put("processStatus", "failed");
						respAction.put("actionMsg", "VERIFIED INVENTORY SHOULD NOT BE GREATER THAN ACTUAL INVENTORY");
						return respAction;
					}
				}

				/*
				 * HERE IT WILL UPDATE THE TRANSACTION AND UPDATE THE COUNT IN THE INVENTORY
				 * DATABASE TABLES
				 */
				for (int i = 0; i < list.size(); i++) {
					System.out.println("===============================================>AVAILABLE");
					Map<String, String> inv = list.get(i);

					String qnt = String.valueOf(inv.get("qnt"));
					System.out.println("=======================QUANTITY============>" + qnt);
					String itemId = String.valueOf(inv.get("itemId"));
					String storeBaysId = String.valueOf(inv.get("storeBaysId"));
					String feQnt = String.valueOf(inv.get("feQnt"));
					String verifiedQnt = String.valueOf(inv.get("verifiedQnt"));

					int qntNumber = Integer.parseInt(qnt);
					int feQntNumber = Integer.parseInt(feQnt);
					int verifiedQntNumber = Integer.parseInt(verifiedQnt);
					

					if (!(qntNumber == verifiedQntNumber)) {
						int addQnt = qntNumber - verifiedQntNumber;
						// update inv_items_quantity_ count (add unused items to database)
						misReportBlockRepository.updateVerifiedInvItemsQuatity(itemId, storeBaysId, addQnt);
						String trxType = "credit";
						String qntTrx = String.valueOf(addQnt);
						// insert transaction in the "inv_trx_stores_" table that ticket has allocated
						this.insertTrxToInvTrxStores(requestMap, qntTrx, itemId, storeBaysId, trxType);
					}
				}

				/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY_RETURNS column */
				misReportBlockRepository.updateInventoryBlockTableVerified(ticketBlockId,
						String.valueOf(new JSONObject(resultSet)));

				/* update to ticket_process_blocks BLOCK_INVENTORY_RETURNS column */
				misReportBlockRepository.updateTicketProcessBlocksVerified(ticketBlockId,
						String.valueOf(new JSONObject(resultSet)));

//					// mis
//					misReportBlockRepository.updateBlockMISActionStatus(actionStatus, ticketBlockId);
//						
//					// Ops
//					misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);
				respAction.put("processStatus", "success");
				respAction.put("actionStatus", actionStatus);
				respAction.put("blockStatus", actionStatus);
				respAction.put("processAt", processAt);
				respAction.put("actionMsg", "Action Message");

			} catch (Exception e) {
				respAction.put("processStatus", "failed");
				e.printStackTrace();
			}
			return respAction;
		}

		@SuppressWarnings("unchecked")
		private String getBlockInventoryVerified(Map<String, Object> requestMap, String ticketBlockId) {

			System.out.println("===============================================INVENTORY IS CHECKING======================");
			Map<String, Object> inventoryData = misReportBlockRepository.getBlockInventoryVerifiedDetails(ticketBlockId);
			String data = String.valueOf(inventoryData.get("blockInventoryVerified"));
			System.out.println("********" + data);
			if (data == "null" || data.isBlank()) {
				return "AVAILABLE";
			}
			return "NOT_AVAILABLE";
		}

		private String getBlockInventoryAssign(Map<String, Object> requestMap, String ticketBlockId) {

			Map<String, Object> inventoryData = misReportBlockRepository.getBlockInventoryAssignDetails(ticketBlockId);
			String blockInventory = String.valueOf(inventoryData.get("blockInventory"));

			if (blockInventory == "null" || blockInventory.isBlank()) {
				return "AVAILABLE";
			}

			return "NOT_AVAILABLE";
		}

		@SuppressWarnings("unchecked")
		private Map<String, String> wInventoryBlockAddition(Map<String, Object> requestMap, String ticketBlockId) {
			Map<String, String> respAction = new HashMap<String, String>();

			try {

				Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
				String processAt = String.valueOf(resultSet.get("processAt"));
				String actionStatus = String.valueOf(resultSet.get("actionStatus"));

				/* getData from database for blockInventoryAssign */
				Map<String, Object> inventoryAssignedData = misReportBlockRepository.getBlockInventoryByTktBlockId(ticketBlockId);

				String jsonData = (String) inventoryAssignedData.get("blockInventory");

				/* insert if field is null || update if data is already existing */

				if (jsonData == null || jsonData.isBlank()) {

					System.out.println(
							"=============>ASSIGNING INVENTORY BECAUSE PREVIOUSLY IT IS NOT ASSIGNED<===============");
					@SuppressWarnings("rawtypes")
					List<Map> list = (List<Map>) resultSet.get("jobInventory");

					for (int i = 0; i < list.size(); i++) {
						Map<String, String> inv = list.get(i);

						String qnt = String.valueOf(inv.get("qnt"));
						String itemId = String.valueOf(inv.get("itemId"));
						String storeBaysId = String.valueOf(inv.get("storeBaysId"));

						System.out.println("<===========checking Inventory====>");

						/* Check the Inventory Availability Status // returns if stock is not there */
						String inventoryStatus = this.checkInventoryStatus(itemId, storeBaysId, qnt);

						if (!inventoryStatus.equals("available")) {
							System.out.println("<=================INV NOT AVAILABLE");
							respAction.put("processStatus", "failed");
							respAction.put("actionMsg", inventoryStatus);
							return respAction;
						}
					}

					/*
					 * HERE IT WILL UPDATE THE TRANSACTION AND UPDATE THE COUNT IN THE INVENTORY
					 * DATABASE TABLES
					 */

					for (int i = 0; i < list.size(); i++) {
						System.out.println("<============= INV AVAILABLE");
						Map<String, String> inv = list.get(i);

						String qnt = String.valueOf(inv.get("qnt"));
						String itemId = String.valueOf(inv.get("itemId"));
						String storeBaysId = String.valueOf(inv.get("storeBaysId"));

						// insert transaction in the "inv_trx_stores_" table that ticket has allocated
						// with this qnt & update inv_items_quantity_ "qnt"

						String trxType = "debit";
						this.insertTrxToInvTrxStores(requestMap, qnt, itemId, storeBaysId, trxType);

						// update inv_items_quantity_ count
						misReportBlockRepository.updateInvItemsQuatity(itemId, storeBaysId, qnt);
					}
					/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY column */

					misReportBlockRepository.updateInventoryBlockTable(ticketBlockId,
							String.valueOf(new JSONObject(resultSet)));

					// update to ticket_process_blocks
					misReportBlockRepository.updateTicketProcessBlocks(ticketBlockId,
							String.valueOf(new JSONObject(resultSet)));

					respAction.put("processStatus", "success");
					respAction.put("actionStatus", actionStatus);
					respAction.put("blockStatus", actionStatus);
					respAction.put("processAt", processAt);
					respAction.put("actionMsg", "Action Message");
					return respAction;
				}

				/* updating the Re-assigned inventory */

				System.out.println("<====updating the assigning NEW inventory=====>");

				Map<String, Object> blockInventory = new Gson().fromJson(jsonData, Map.class);

//					========>Validating the assigned inventory with database

				List<Map> list = (List<Map>) resultSet.get("jobInventory");

				for (int i = 0; i < list.size(); i++) {
					Map<String, String> inv = list.get(i);

					String qnt = String.valueOf(inv.get("qnt"));
					String itemId = String.valueOf(inv.get("itemId"));
					String storeBaysId = String.valueOf(inv.get("storeBaysId"));

					System.out.println("<==========>" + storeBaysId + itemId + qnt);

					/* Check the Inventory Availability Status // returns if stock is not there */
					String inventoryStatus = this.checkInventoryStatus(itemId, storeBaysId, qnt);

					if (!inventoryStatus.equals("available")) {
						System.out.println("<=================INV NOT AVAILABLE");
						respAction.put("processStatus", "failed");
						respAction.put("actionMsg", inventoryStatus);
						return respAction;
					}
				}
//					======== New Logic from Merging & adding
				List<Map> inputList = (List<Map>) resultSet.get("jobInventory");
				List<Map> dbList = (List<Map>) blockInventory.get("jobInventory");

				List<Map<String, String>> newList = new ArrayList<Map<String, String>>();
				for (int j = 0; j < inputList.size(); j++) {

					Map<String, String> inputListRow = inputList.get(j);

					Integer inputQnt = Integer.parseInt(String.valueOf(inputListRow.get("qnt")));
					String inputItemId = String.valueOf(inputListRow.get("itemId"));

					if (inputQnt >= 0) {
						boolean isExist = false;
						System.out.println("===============Inv==>>" + inputItemId);
						for (int i = 0; i < dbList.size(); i++) {
							Map<String, String> dbInv = dbList.get(i);
							System.out.println("===============DB==>>" + String.valueOf(dbInv));

							Integer newQnt = Integer.parseInt(String.valueOf(dbInv.get("qnt")));
							String dbItemId = String.valueOf(dbInv.get("itemId"));
							if (inputItemId.equals(dbItemId)) {
								isExist = true;

								dbInv.put("qnt", String.valueOf(inputQnt + newQnt));
								newList.add(dbInv);
							}
						}
						if (!isExist) {
							System.out.println("<====Adding Not Exist==>>");
							newList.add(inputListRow);
						} else {
							System.out.println("<<==NA==>>");
						}
					}
				}
				for (int i = 0; i < dbList.size(); i++) {
					Map<String, String> dbInvRow = dbList.get(i);
					String dbItemId = String.valueOf(dbInvRow.get("itemId"));
					boolean ifExist = false;
					for (int j = 0; j < inputList.size(); j++) {
						Map<String, String> inputListRow = inputList.get(j);
						String inputItemId = String.valueOf(inputListRow.get("itemId"));
						if (inputItemId.equals(dbItemId)) {
							ifExist = true;
						}
					}
					if (!ifExist) {
						System.out.println("<=Adding Not Exist==>>");
						newList.add(dbInvRow);
					}
				}
				JSONObject updatedInv = new JSONObject();
				updatedInv.put("jobInventory", newList);
				updatedInv.put("processAt", processAt);
				updatedInv.put("ticketBlockId", ticketBlockId);
				updatedInv.put("actionStatus", actionStatus);
				System.out.println(updatedInv + "<=====>");

//					============>Inserting the transaction and updating the quantity
				List<Map> reAssignList = (List<Map>) resultSet.get("jobInventory");

				for (int i = 0; i < reAssignList.size(); i++) {
					Map<String, String> reAssignInv = reAssignList.get(i);

					String aQnt = String.valueOf(reAssignInv.get("qnt"));
					String aItemId = String.valueOf(reAssignInv.get("itemId"));
					String aStoreBaysId = String.valueOf(reAssignInv.get("storeBaysId"));

					String trxType = "debit";
					this.insertTrxToInvTrxStores(requestMap, aQnt, aItemId, aStoreBaysId, trxType);

					// update inv_items_quantity_ count
					misReportBlockRepository.updateInvItemsQuatity(aItemId, aStoreBaysId, aQnt);
				}

				/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY column */

				misReportBlockRepository.updateInventoryBlockTable(ticketBlockId, String.valueOf(updatedInv));

				// update to ticket_process_blocks
				misReportBlockRepository.updateTicketProcessBlocks(ticketBlockId, String.valueOf(updatedInv));

				respAction.put("processStatus", "success");
				respAction.put("actionStatus", actionStatus);
				respAction.put("blockStatus", actionStatus);
				respAction.put("processAt", processAt);
				respAction.put("actionMsg", "Action Message");
				return respAction;

			} catch (Exception e) {
				respAction.put("processStatus", "failed");
				e.printStackTrace();
			}
			return respAction;
		}

		@SuppressWarnings("unchecked")
		private Map<String, String> wBlockUnusedInventoryReturn(Map<String, Object> requestMap, String ticketBlockId) {
			Map<String, String> respAction = new HashMap<String, String>();

			try {
				Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
				// List<Map<String, Object>> jobVerifiedInventory = (List<Map<String, Object>>)
				// (resultSet.get("jobOldInventory"));

				String actionStatus = String.valueOf(resultSet.get("actionStatus"));
				String processAt = String.valueOf(resultSet.get("processAt"));
				
				
				
				

				List<Map> list = (List<Map>) resultSet.get("jobUnusedInventory");
//				/* VERIFICATION WILL HAPPEN HERE */
//				for (int i = 0; i < list.size(); i++) {
//					System.out.println("===============================================>AVAILABLE");
//					Map<String, String> inv = list.get(i);
//					String qnt = String.valueOf(inv.get("qnt"));
//					String itemId = String.valueOf(inv.get("itemId"));
//					String storeBaysId = String.valueOf(inv.get("storeBaysId"));
//					String unUsedQnt = String.valueOf(inv.get("unUsedQnt"));
//					int qntNumber = Integer.parseInt(qnt);
//					int unUsedQntNumber = Integer.parseInt(unUsedQnt);
//					if (!(unUsedQntNumber == qntNumber)) {
//						System.out.println("=======>entered inv is wrong");
//						respAction.put("processStatus", "failed");
//						respAction.put("actionMsg", "PLEASE ENTER CORRECT INVENTORY");
//						return respAction;
//					}
//				}
				
				/* VERIFICATION WILL HAPPEN IF ALREADY DONE THIS OR NOT*/
				String statusUpdate = this.getBlockUnusedInventoryVerified(ticketBlockId);

				if (statusUpdate == "NOT_AVAILABLE") {
					System.out.println("========>UNUSED INVENTORY IS ALREADY VERIFIED");
					respAction.put("processStatus", "failed");
					respAction.put("actionMsg", "Unused Inventory is already verified");
					return respAction;
				}
				System.out.println("CAME OUT"); 
				
				/* INSERTIONS WILL HAPPEN HERE */
				for (int i = 0; i < list.size(); i++) {
					Map<String, String> inv = list.get(i);
					String qnt = String.valueOf(inv.get("qnt"));
					String itemId = String.valueOf(inv.get("itemId"));
					String storeBaysId = String.valueOf(inv.get("storeBaysId"));
					String unUsedQnt = String.valueOf(inv.get("unUsedQnt"));
					int unUsedQntNumber = Integer.parseInt(unUsedQnt);
					int qntNumber = Integer.parseInt(qnt);
					
					int addQntNumber = qntNumber - unUsedQntNumber;
					
					String addQnt = String.valueOf(addQntNumber);
					
					/* update inv_items_quantity_ count (add unused items to database) */
					misReportBlockRepository.updateVerifiedInvItemsQuatity(itemId, storeBaysId, addQntNumber);
					String trxType = "credit";
					/*
					 * insert transaction in the "inv_trx_stores_" table that ticket has allocated
					 */
					this.insertTrxToInvTrxStores(requestMap, addQnt, itemId, storeBaysId, trxType);
				}
				
				/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY_UNUSED column */

				misReportBlockRepository.updateUnusedInventoryBlockMisTable(ticketBlockId, String.valueOf(resultSet));

				// update to ticket_process_blocks table BLOCK_INVENTORY_UNUSED
				misReportBlockRepository.updateUnusedInventoryProcessBlocks(ticketBlockId, String.valueOf(resultSet));

				respAction.put("processStatus", "success");
				respAction.put("actionStatus", actionStatus);
				respAction.put("blockStatus", actionStatus);
				respAction.put("processAt", processAt);
				respAction.put("actionMsg", "Action Message");

			} catch (Exception e) {
				respAction.put("processStatus", "failed");
				e.printStackTrace();
			}

			return respAction;
		}
		
		private String getBlockUnusedInventoryVerified(String ticketBlockId) {

			System.out.println("======UNUSED INVENTORY IS CHECKING======================");
			Map<String, Object> invUnusedData = misReportBlockRepository
					.getBlockUnusedInventoryVerifiedDetails(ticketBlockId);
			String inventoryData = String.valueOf(invUnusedData.get("blockInventoryUnused"));

			if (inventoryData == "null" || inventoryData.isBlank()) {
				System.out.println("AVAILABLE");
				return "AVAILABLE";
			}

			System.out.println("NOT_AVAILABLE");
			return "NOT_AVAILABLE";

		}

	// ONE ASSIST BUYBACK ACTIVITY

	@SuppressWarnings("unchecked")
	private Map<String, String> mBuyBackStatus(Map<String, Object> requestMap, String ticketBlockId,
			String ticketProductId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String conComplaintNo = String.valueOf(resultSet.get("conComplaintNo"));
			String orderNo = String.valueOf(resultSet.get("orderNo"));
			String valueOffered = "";
			String status="";

			if (String.valueOf(resultSet.get("actionStatus")).equals("RE_QUOTE")) {
				JSONObject requestJson = getOAJsonForRequote(conComplaintNo);

				String url = RET_STATUS_UPDATE_URL_OA_BUYBACK_REQUOTE;
				String baseAuth = RET_STATUS_UPDATE_URL_OA_BUYBACK_REQUOTE_BASIC_AUTH;

				String rawResponse = callApiPostBaseauth(url, baseAuth, requestJson);
				
				System.out.println("RAWRESPONSE------------------->"+rawResponse);

				Map<String, Object> requoteResult = new HashMap<String, Object>();
				requoteResult.put("reqJson", requestJson);
				requoteResult.put("rawResponse", rawResponse);
				this.logAPIError(requoteResult);

				JSONObject responseJson = new JSONObject(rawResponse);
				status = responseJson.getString("status");

				if (status.equals("success")) {
//			        	 System.out.println("INSIDE IF");
					JSONObject responseDate = (JSONObject) responseJson.get("data");

					valueOffered = responseDate.getString("price");

//					response.resp200(updatedPrice, responseJson.getString("message"));
				} else {
//			        	 System.out.println("INSIDE ELSE");
					valueOffered = responseJson.getString("message");
//					response.respFail(updatedPrice, responseJson.getString("message"));
				}
			}

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String blockStatus = actionStatus;
			String processAt = String.valueOf(resultSet.get("processAt"));
			String flowStatus = String.valueOf(requestMap.get("flowStatus"));

			if (flowStatus.equals("") || flowStatus.equals(null)) {
				flowStatus = "PICK_MOB";
			}

			switch (actionStatus) {
			case "ACCEPTED":
				blockStatus = "CUSTOMER_AGREED_WITH_THE_VALUE";
				break;
			case "REJECTED":
				blockStatus = "CUSTOMER_DISAGREE_WITH_THE_VALUE";
				flowStatus = "PICK_MOB";
				break;
			case "RE_QUOTE":
				blockStatus = "CUSTOMER_ASK_FOR_REQUOTE";
				flowStatus = "PICK_MOB";
				break;
			default:
				blockStatus = actionStatus;
			}

			// MIS
			misReportBlockRepository.updateBlockMISBlockActionStatus(blockStatus, actionStatus, ticketBlockId,
					getDateAndTime());
			misReportBlockRepository.updateProdMISActionStatus(actionStatus, ticketProductId);
			misReportBlockRepository.updateProdMISActionStatus(blockStatus, ticketProductId);

			// Ops
			misReportBlockRepository.updateBlockOpsBlockStatus(flowStatus, blockStatus, ticketBlockId);
			misReportBlockRepository.updateBlockOpsActionStatus(actionStatus, ticketBlockId);

			misReportBlockRepository.updateProdOpsActionStatus(blockStatus, ticketProductId);
			respAction.put("processStatus", status);
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", blockStatus);
			respAction.put("processAt", processAt);
			respAction.put("valueOffered", valueOffered);

			respAction.put("actionMsg", "Action Message");
			System.out.println("valueOffered--->" + valueOffered);
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}
		return respAction;

	}

	private static JSONObject getOAJsonForRequote(String conComplaintNo) throws JSONException {
		JSONObject requestJson = new JSONObject();

		requestJson.put("orderId", conComplaintNo);
		requestJson.put("createdBy", "Bizlog");
		requestJson.put("createdByType", "LOGISTIC_USER");
		return requestJson;
	}

	@SuppressWarnings("unchecked")
	public Map<String, String> mPhysicEvalResult(Map<String, Object> requestMap, String ticketBlockId,
			String ticketProductId) {
		Map<String, String> respAction = new HashMap<String, String>();

		System.out.println("******REQUEST***********" + requestMap);

//				Map<String, Object> requestMap = new Gson().fromJson(reqJson, Map.class);

//				System.out.println("******REQMAP***********"+requestMap);

		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

//					System.out.println("******resultSet***********"+resultSet);

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String conComplaintNo = String.valueOf(resultSet.get("conComplaintNo"));
			String orderNo = String.valueOf(resultSet.get("orderNo"));
			String processAt = String.valueOf(resultSet.get("processAt"));

//					List<Object> resArray = new ArrayList<Object>();

//					ArrayList<Object> resArray = new ArrayList<Object>();
			List resArray = new ArrayList<>();

			if (actionStatus.equals("PHYSICAL_EVALUATION_RESULT")) {

				ArrayList<Map<String, String>> list = (ArrayList<Map<String, String>>) resultSet.get("phyEvalQC");

//						System.out.println("SIZE--->"+list.size());

				for (int i = 0; i < list.size(); i++) {
					Map<String, Object> res = new HashMap<String, Object>();
					res.clear();
					String questionId = list.get(i).get("id");
					String answer = list.get(i).get("resp");
					
//							System.out.println("QUESTION--"+i+"--->"+questionId);
//							System.out.println("ANSWER--"+i+"--->"+answer);

					res.put("questionId", questionId);
					res.put("answer", answer);

					System.out.println("RES--->" + res);

//							res.put("questionId", questionId);
//							res.put("answer", answer);

					resArray.add(i, res);

					System.out.println("EVALUTION DATA--->" + resArray);

				}

			}

			if (conComplaintNo.isBlank() || conComplaintNo.isEmpty() || conComplaintNo.equals("null")) {
				conComplaintNo = orderNo;
			}

			JSONObject requestJson = getOneAssistJsonForEvaluationResult(conComplaintNo, resArray);

			System.out.println("REQUEST JSON--->" + requestJson);

			String url = RET_STATUS_UPDATE_URL_OA_BUYBACK_QC_RESULT;
			String baseAuth = RET_STATUS_UPDATE_URL_OA_BUYBACK_QC_RESULT_BASIC_AUTH;

			String rawResponse = callApiPostBaseauth(url, baseAuth, requestJson);

			System.out.println("AFTER SAVED RESULT TO CLIENT--->" + rawResponse);

			Map<String, Object> qcResult = new HashMap<String, Object>();
			qcResult.put("reqJson", requestJson);
			qcResult.put("rawResponse", rawResponse);
			this.logAPIError(qcResult);

			JSONObject responseJson = new JSONObject(rawResponse);
			String status = responseJson.getString("status");
//			if (status.equals("success")) {
//				respAction.put("responseStatus", status);
//			        	 System.out.println("INSIDE IF");
//					response.resp200(null, responseJson.getString("message"));
//			} else {
//				respAction.put("responseStatus", status);
//			        	 System.out.println("INSIDE ELSE");
//					response.respFail(null, responseJson.getString("message"));
//			}

			respAction.put("processStatus", status);
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", actionStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", responseJson.getString("message"));

		} catch (Exception e) {

			e.printStackTrace();
		}
		return respAction;

	}

	private static JSONObject getOneAssistJsonForEvaluationResult(String conComplaintNo, List<Object> resArray) {
		JSONObject requestJson = new JSONObject();

		try {
			requestJson.put("orderId", conComplaintNo);
			requestJson.put("questionType", "QUOTE_QUESTION");
			requestJson.put("createdBy", "bizlog");
			requestJson.put("createdByType", "LOGISTIC_USER");
			requestJson.put("data", resArray);

		} catch (JSONException e) {
			e.printStackTrace();
		}
		return requestJson;
	}
	
	@SuppressWarnings("unchecked")
	private Map<String, String> wInvoice(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {
			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));

			switch (actionStatus) {
			case ("READY_FOR_INVOICE"):
				respAction = this.readyForInvoice(requestMap, ticketBlockId);
				return respAction;

			case ("INVOICE_NOT_REQUIRED"):
				respAction = this.invoiceNotRequired(requestMap, ticketBlockId);
				return respAction;
				
			case ("INVOICE_GENERATED"):
				respAction = this.invoiceGenerated(requestMap, ticketBlockId);
				return respAction;
			case ("INVOICE_PAID"):
				respAction = this.invoicePaid(requestMap, ticketBlockId);
				return respAction;
			default:
				respAction.put("processStatus", "failed");
				respAction.put("actionMsg", "incorrect status");
				return respAction;
			}
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
			return respAction;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> readyForInvoice(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String remarks = String.valueOf(resultSet.get("remarks"));

			// update in ticket process blocks with remarks & status
			misReportBlockRepository.updateInvoiceReadyProcessBlocks(ticketBlockId, remarks, actionStatus);

			// update in zzz_ticket_mis_blocks
			misReportBlockRepository.updateInvoiceReadyMisBlocks(ticketBlockId, remarks, actionStatus);

			System.out.println("done<====================================");

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", actionStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

			System.out.println("done2<====================================");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> invoiceNotRequired(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String remarks = String.valueOf(resultSet.get("remarks"));

			// update in ticket process blocks with remarks & status
			misReportBlockRepository.updateInvoiceReadyProcessBlocks(ticketBlockId, remarks, actionStatus);

			// update in zzz_ticket_mis_blocks
			misReportBlockRepository.updateInvoiceReadyMisBlocks(ticketBlockId, remarks, actionStatus);

			System.out.println("done<====================================");

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", actionStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

			System.out.println("done2<====================================");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> invoiceGenerated(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String remarks = String.valueOf(resultSet.get("remarks"));
			String invoiceNumber = String.valueOf(resultSet.get("invoiceNumber"));
			String invoiceAmount = String.valueOf(resultSet.get("invoiceAmount"));

			// update in ticket process blocks with remarks & status
			misReportBlockRepository.updateInvoiceGeneratedProcessBlocks(ticketBlockId, remarks, actionStatus,
					invoiceNumber, invoiceAmount);

			// update in zzz_ticket_mis_blocks
			misReportBlockRepository.updateInvoiceGeneratedMisBlocks(ticketBlockId, remarks, actionStatus,
					invoiceNumber, invoiceAmount);

			System.out.println("done<====================================");

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", actionStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

			System.out.println("done2<====================================");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> invoicePaid(Map<String, Object> requestMap, String ticketBlockId) {
		Map<String, String> respAction = new HashMap<String, String>();
		try {

			Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));

			String actionStatus = String.valueOf(resultSet.get("actionStatus"));
			String processAt = String.valueOf(resultSet.get("processAt"));
			String remarks = String.valueOf(resultSet.get("remarks"));
			String invoiceNumber = String.valueOf(resultSet.get("invoiceNumber"));
			String invoicePaidAmount = String.valueOf(resultSet.get("invoicePaidAmount"));

			// update in ticket process blocks with remarks & status
			misReportBlockRepository.updateInvoicePaidProcessBlocks(ticketBlockId, remarks, actionStatus, invoiceNumber, invoicePaidAmount);

			// update in zzz_ticket_mis_blocks
			misReportBlockRepository.updateInvoicePaidProcessBlocks(ticketBlockId, remarks, actionStatus, invoiceNumber, invoicePaidAmount);

			System.out.println("done<====================================");

			respAction.put("processStatus", "success");
			respAction.put("actionStatus", actionStatus);
			respAction.put("blockStatus", actionStatus);
			respAction.put("processAt", processAt);
			respAction.put("actionMsg", "Action Message");

			System.out.println("done2<====================================");
		} catch (Exception e) {
			respAction.put("processStatus", "failed");
			e.printStackTrace();
		}

		return respAction;
	}

	

}

// @SuppressWarnings("unchecked")
//		private Map<String, String> wInventoryBlockAddition(Map<String, Object> requestMap, String ticketBlockId) {
//			Map<String, String> respAction = new HashMap<String, String>();
//			
//			try {
//
//				Map<String, Object> resultSet = (Map<String, Object>) (requestMap.get("resultSet"));
//				String processAt = String.valueOf(resultSet.get("processAt"));
//				String actionStatus = String.valueOf(resultSet.get("actionStatus"));
//
//				/* getData from database for blockInventoryAssign */
//				Map<String, Object> inventoryAssignedData = misReportBlockRepository.getBlockInventoryByTktBlockId(ticketBlockId);
//				
//				String jsonData = (String) inventoryAssignedData.get("blockInventory");
//
//				/* insert if field is null || update if data is already existing */
//
//				if (jsonData == null || jsonData.isBlank()) {
//
//					System.out.println(
//							"=============>ASSIGNING INVENTORY BECAUSE PREVIOUSLY IT IS NOT ASSIGNED<===============");
//					List<Map> list = (List<Map>) resultSet.get("jobInventory");
//
//					for (int i = 0; i < list.size(); i++) {
//						Map<String, String> inv = list.get(i);
//
//						String qnt = String.valueOf(inv.get("qnt"));
//						String itemId = String.valueOf(inv.get("itemId"));
//						String storeBaysId = String.valueOf(inv.get("storeBaysId"));
//
//						System.out.println("===================storeBaysId============================" + storeBaysId);
//
//						/* Check the Inventory Availability Status // returns if stock is not there */
//						String inventoryStatus = this.checkInventoryStatus(itemId, storeBaysId, qnt);
//
//						if (!inventoryStatus.equals("available")) {
//							System.out.println("===============================================NOT AVAILABLE");
//							respAction.put("processStatus", "failed");
//							respAction.put("actionMsg", inventoryStatus);
//							return respAction;
//						}
//					}
//
//					/*
//					 * HERE IT WILL UPDATE THE TRANSACTION AND UPDATE THE COUNT IN THE INVENTORY
//					 * DATABASE TABLES
//					 */
//
//					for (int i = 0; i < list.size(); i++) {
//						System.out.println("===============================================AVAILABLE ******************");
//						Map<String, String> inv = list.get(i);
//
//						String qnt = String.valueOf(inv.get("qnt"));
//						String itemId = String.valueOf(inv.get("itemId"));
//						String storeBaysId = String.valueOf(inv.get("storeBaysId"));
//
//						// insert transaction in the "inv_trx_stores_" table that ticket has allocated
//						// with this qnt & update inv_items_quantity_ "qnt"
//
//						String trxType = "debit";
//						this.insertTrxToInvTrxStores(requestMap, qnt, itemId, storeBaysId, trxType);
//
//						// update inv_items_quantity_ count
//						misReportBlockRepository.updateInvItemsQuatity(itemId, storeBaysId, qnt);
//					}
//					/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY column */
//
//					misReportBlockRepository.updateInventoryBlockTable(ticketBlockId,
//							String.valueOf(new JSONObject(resultSet)));
//
//					// update to ticket_process_blocks
//					misReportBlockRepository.updateTicketProcessBlocks(ticketBlockId,
//							String.valueOf(new JSONObject(resultSet)));
//					
//				//	String processAt = String.valueOf(resultSet.get("processAt"));
//				//	String actionStatus = String.valueOf(resultSet.get("actionStatus"));
//					
//					respAction.put("processStatus", "success");
//					respAction.put("actionStatus", actionStatus);
//					respAction.put("blockStatus",actionStatus);
//					respAction.put("processAt", processAt);
//					respAction.put("actionMsg", "Action Message");
//					return respAction;
//				}
//				
//				/* updating the assigning inventory */
//
//				System.out.println("++++++++++++++++++++updating the assigning inventory+++++++++++++++++++++++");
//
//				JSONArray jobInventory = new JSONArray();
//				
//				Map<String, Object> blockInventory = new Gson().fromJson(jsonData, Map.class);
//
//				List<Map> dbList = (List<Map>) blockInventory.get("jobInventory");
//			
//				System.out.println(dbList.size() + "==================dbList.size()============================");
//				/* validating inputs */
//				for (int i = 0; i < dbList.size(); i++) {
//					System.out.println(dbList.size() + "<===============================dbList.size()=======");
//					Map<String, String> dbInv = dbList.get(i);
//					String dbQnt = String.valueOf(dbInv.get("qnt"));
//					String dbItemId = String.valueOf(dbInv.get("itemId"));
//					String dbStoreBaysId = String.valueOf(dbInv.get("storeBaysId"));
//					
//					List<Map> list = (List<Map>) resultSet.get("jobInventory");
//
//					for (int j = 0; j < list.size(); j++) {
//						Map<String, String> inv = list.get(j);
//
//						String qnt = String.valueOf(inv.get("qnt"));
//						String itemId = String.valueOf(inv.get("itemId"));
//						String storeBaysId = String.valueOf(inv.get("storeBaysId"));
//
//						System.out.println("===================storeBaysId============================" + storeBaysId);
//
//						/* Check the Inventory Availability Status // returns if stock is not there */
//						String inventoryStatus = this.checkInventoryStatus(itemId, storeBaysId, qnt);
//
//						if (!inventoryStatus.equals("available")) {
//							System.out.println("===============================================NOT AVAILABLE");
//							respAction.put("processStatus", "failed");
//							respAction.put("actionMsg", inventoryStatus);
//							return respAction;
//						}
//						System.out.println(dbItemId + itemId + "<=============dbItemId + itemId======================");
//						
//						if (dbItemId.equals(itemId)) {
//							int adQnt = Integer.parseInt(qnt);
//							int adDbQnt = Integer.parseInt(dbQnt);
//							adDbQnt = adQnt + adDbQnt;
//							String updatedQnt = String.valueOf(adDbQnt);
//							dbInv.put("qnt", updatedQnt);
//							
//							String trxType = "debit";
//							this.insertTrxToInvTrxStores(requestMap, qnt, itemId, storeBaysId, trxType);
//
//							// update inv_items_quantity_ count
//							misReportBlockRepository.updateInvItemsQuatity(itemId, storeBaysId, qnt);
//						} else {
//							
//							jobInventory.put(inv);
//							
//							/* insert transaction */
//							String trxType = "debit";
//							 this.insertTrxToInvTrxStores(requestMap, qnt, itemId, storeBaysId, trxType);
//
//							/* update inv_items_quantity_ count */
//							 misReportBlockRepository.updateInvItemsQuatity(itemId, storeBaysId, qnt);
//						}
//						/* 2ND for loop ends */
//					}
//					System.out.println(dbInv.toString() + "<=================dbInv.toString()====================================");
//					jobInventory.put(dbInv);
//					
//					System.out.println("================UPDATED VALUES=====>" + jobInventory);
//					/* 1st for loop ends */
//				}
//				JSONObject updatedInv = new JSONObject();
//				updatedInv.put("jobInventory", jobInventory);
//				updatedInv.put("ticketBlockId", String.valueOf(resultSet.get("ticketBlockId")));
//				updatedInv.put("actionStatus", String.valueOf(resultSet.get("actionStatus")));
//				updatedInv.put("processAt", String.valueOf(resultSet.get("processAt")));
//
//				System.out.println(updatedInv.toString() + "<======================================");
//
//				/* updating zzz_ticket_mis_report_block_ table BLOCK_INVENTORY column */
//				
//				misReportBlockRepository.updateInventoryBlockTable(ticketBlockId,String.valueOf(updatedInv));
//		
//				//update to ticket_process_blocks
//				misReportBlockRepository.updateTicketProcessBlocks(ticketBlockId,String.valueOf(updatedInv));
//				
//				respAction.put("processStatus", "success");
//				respAction.put("actionStatus", actionStatus);
//				respAction.put("blockStatus",actionStatus);
//				respAction.put("processAt", processAt);
//				respAction.put("actionMsg", "Action Message");
//				return respAction;
//
//			} catch (Exception e) {
//				respAction.put("processStatus", "failed");
//				e.printStackTrace();
//			}
//			return respAction;
//		} */

