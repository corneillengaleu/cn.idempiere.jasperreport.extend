package cn.idempiere.jasperreport.extend.process;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Level;

import org.compiere.model.MBPartner;
import org.compiere.model.MClient;
import org.compiere.model.MMailText;
import org.compiere.model.MPInstance;
import org.compiere.model.MProcess;
import org.compiere.model.MSysConfig;
import org.compiere.model.MTable;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.ServerProcessCtl;
import org.compiere.process.SvrProcess;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.EMail;
import org.compiere.util.Env;

import cn.idempiere.jasperreport.extend.util.PdfEncryptor;

public class SendEmailEncryptedJasperReport extends SvrProcess {

	private String AD_Process_Value;
	private boolean isEncrypted = false;
//	private String eMailTest = "";
	private Integer p_HR_Process_ID;
	private Integer p_AD_User_ID;
	private boolean p_IsOption1;
	private String p_IsOption2;
	private static final CLogger log = CLogger.getCLogger(SendEmailEncryptedJasperReport.class);

	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if ("AD_Process_Value".equalsIgnoreCase(para[i].getParameterName()))
				AD_Process_Value = para[i].getParameterAsString(); // just for get correct report to run
			else if ("isEncrypted".equalsIgnoreCase(para[i].getParameterName()))
				isEncrypted = para[i].getParameterAsBoolean(); // to control apply password or not
			else if ("HR_Process_ID".equalsIgnoreCase(para[i].getParameterName()))
				p_HR_Process_ID = para[i].getParameterAsInt();
			else if ("IsOption1".equalsIgnoreCase(para[i].getParameterName()))
				p_IsOption1 = para[i].getParameterAsBoolean();
			else if ("IsOption2".equalsIgnoreCase(para[i].getParameterName()))
				p_IsOption2 = para[i].getParameterAsString();
			else if ("AD_User_ID".equalsIgnoreCase(para[i].getParameterName()))
				p_AD_User_ID = para[i].getParameterAsInt();
			else
				log.log(Level.SEVERE, "prepare - Unknown Parameter: " + name);
		}
	}

	@Override
	protected String doIt() throws Exception {
		int sentCount = 0;
		int failCount = 0;


		    MClient client = MClient.get(getCtx());

		    // Determiner l'expéditeur
		    String expediteurEMail = MSysConfig.getValue("MAIL_XXX_XXXXXX");
		    String senderEmail = null;

		    if ("U".equalsIgnoreCase(expediteurEMail)) {
		        MUser sender = new MUser(getCtx(), p_AD_User_ID, null);
		        senderEmail = sender.getEMail();
		    } else if ("C".equalsIgnoreCase(expediteurEMail)) {
		        senderEmail = client.getRequestEMail();
		    } else if ("S".equalsIgnoreCase(expediteurEMail)) {
		        senderEmail = MClient.get(getCtx(), 0).getRequestEMail();
		    }

		    if (senderEmail == null || senderEmail.isEmpty()) {
		        return "Expéditeur sans e-mail.";
		    }

		    // Recuperer tous les mouvements du HR_Process
		    String sql = "SELECT HR_Movement_ID FROM HR_Movement WHERE AD_Client_ID=? AND HR_Process_ID=? AND IsActive='Y'";
		    List<List<Object>> movements = DB.getSQLArrayObjectsEx(null, sql, new Object[]{getAD_Client_ID(), p_HR_Process_ID});
		    if (movements == null || movements.isEmpty()) {
		        return "Aucun mouvement trouvé pour le HR_Process_ID=" + p_HR_Process_ID;
		    }

		    for (List<Object> li : movements) {
		        int HR_Movement_ID = ((BigDecimal) li.get(0)).intValue();
		        PO poMovement = MTable.get(getCtx(), "HR_Movement").getPO(HR_Movement_ID, null);
		        int C_BPartner_ID = poMovement.get_ValueAsInt("C_BPartner_ID");
		        MBPartner mbPartner=MBPartner.get(getCtx(), C_BPartner_ID);

		        // Recuperer email(s) du partenaire
		        String sqlUser = "SELECT EMail FROM C_BPartner WHERE AD_Client_ID=? AND C_BPartner_ID=? AND iEXT_EMailRecipient='Y' AND IsActive='Y' AND EMail IS NOT NULL";
		        List<List<Object>> userList = DB.getSQLArrayObjectsEx(null, sqlUser, new Object[]{poMovement.getAD_Client_ID(), C_BPartner_ID});

		        if (userList == null || userList.isEmpty()) {
	                failCount++;
		            log.warning("Pas de destinataire pour C_BPartner_ID=" + C_BPartner_ID);
		            continue;
		        }

		        for (List<Object> liu : userList) {
		            String EMail = liu.get(0).toString();
		            log.info("Préparation du mail pour " + EMail + " HR_Movement_ID=" + HR_Movement_ID);

		            // Preparer le ProcessInfo pour le JasperReport
		            Query queryReport = new Query(getCtx(), "AD_Process", "AD_Client_ID=? AND Value=?", null);
		            queryReport.setParameters(0, AD_Process_Value);
		            int processId = queryReport.firstIdOnly();
		            MProcess m_process = MProcess.get(getCtx(), processId);

		            ProcessInfo pi = new ProcessInfo(m_process.getName(), processId, poMovement.get_Table_ID(), HR_Movement_ID);
		            pi.setAD_User_ID(Env.getAD_User_ID(getCtx()));
		            pi.setAD_Client_ID(getAD_Client_ID());
		            pi.setExport(true);
		            pi.setReportingProcess(true);
		            pi.setTransactionName(poMovement.get_TrxName());
		        
		            
		           

		            // Paramètres pour le Jasper
		            ProcessInfoParameter[] paras = new ProcessInfoParameter[]{
		                    new ProcessInfoParameter("isEncrypted", isEncrypted, null, null, null),
		                    new ProcessInfoParameter("createPassword", "adminPassword", null, null, null),
		                    new ProcessInfoParameter("readPassword", "userPassword", null, null, null),
		                    new ProcessInfoParameter("AD_Client_ID", getAD_Client_ID(), null, null, null),
		                    new ProcessInfoParameter("HR_Movement_ID", HR_Movement_ID, null, null, null),
		                    new ProcessInfoParameter("AD_User_ID", p_AD_User_ID, null, null, null),
		                    new ProcessInfoParameter("IsOption1", p_IsOption1, null, null, null),
		                    new ProcessInfoParameter("IsOption2", p_IsOption2, null, null, null)
		            };
		            pi.setParameter(paras);

		            // Executer le process JasperReport
		            MPInstance instance = new MPInstance(getCtx(), 0, get_TrxName());
		            instance.setAD_Process_ID(processId);
		            instance.setRecord_ID(HR_Movement_ID);
		            instance.saveEx();
		            pi.setAD_PInstance_ID(instance.getAD_PInstance_ID());

		         
		           
		            ServerProcessCtl ctl = ServerProcessCtl.process(pi, null);
		            ctl.run();
		            
		            // Verifier le PDF genere
		            File pdfFile = pi.getExportFile();
		            if (pdfFile == null || !pdfFile.exists()) {
		                failCount++;
		                log.warning("PDF non généré pour " + EMail);
		                continue;
		            }


		            // Crypter le PDF
		            File encryptedPdf = PdfEncryptor.encryptPdf(pdfFile, mbPartner.get_ValueAsString("Password")!=null?mbPartner.get_ValueAsString("Password"):mbPartner.get_ValueAsString("IEXT_emp_matricula").concat("@-"), "adminPassword");		            
		            pi.setExportFile(encryptedPdf);
		            log.warning("Access pdf==="+mbPartner.getValue().concat(mbPartner.get_ValueAsString("IEXT_NumberCNPS")));
		            // Préparer le mail
		            String r_MailText_Value = "RH_PaySlipOfEmployee";
		            int R_MailText_ID = DB.getSQLValue(null, "SELECT R_MailText_ID FROM R_MailText WHERE Value=? AND IsActive='Y'", r_MailText_Value);
		            if (R_MailText_ID <= 0) {
		                failCount++;
		                log.warning("MailText introuvable : " + r_MailText_Value);
		                continue;
		            }
		            MMailText mailText = new MMailText(getCtx(), R_MailText_ID, null);
		            mailText.setBPartner(mbPartner);
		            mailText.setPO(poMovement);
		            
		            StringBuilder message = new StringBuilder(mailText.getMailText(true));
		            EMail email = client.createEMailFrom(senderEmail, EMail, mailText.getMailHeader(), message.toString(), false);
		            if (mailText.isHtml()) {
		                email.setMessageHTML(mailText.getMailHeader(), message.toString());
		            } else {
		                email.setSubject(mailText.getMailHeader());
		                email.setMessageText(message.toString());
		            }

		            email.addAttachment(encryptedPdf);
		            String result = email.send();
		            if (!"OK".equals(result)) {
		                log.warning("Échec envoi à " + EMail + " : " + result);
		                failCount++;

		            } else {
			            poMovement.set_ValueNoCheck("iEXT_EmailSent", true);
			            poMovement.save();

		                log.info("Email envoyé avec succès à " + EMail);
		                sentCount++;

		            }
		        }
		    }

		    return "Process terminé. Emails envoyés : " + sentCount + ", échecs : " + failCount;
		}


}
