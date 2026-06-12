package cn.idempiere.jasperreport.extend.factories;

import org.adempiere.base.IProcessFactory;
import org.compiere.process.ProcessCall;
import org.osgi.service.component.annotations.Component;

import cn.idempiere.jasperreport.extend.process.SendEmailEncryptedJasperReport;


@Component(
	property= {"service.ranking:Integer=100"},
	service = org.adempiere.base.IProcessFactory.class
	)
public class SendEmailEncryptedJasperReportFactory implements IProcessFactory{

	@Override
	public ProcessCall newProcessInstance(String className) {
		// TODO Auto-generated method stub
		if(className.equals("cn.idempiere.jasperreport.extend.Process_SendEmailEncryptedJasperReport")) {
			return new SendEmailEncryptedJasperReport();
		}
		return null;
	}

}
