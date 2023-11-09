package com.example.demo.controller;

import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppClient;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppBindException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import com.example.demo.ApplicationProperties;


@RestController
public class optController {
	
	private static final Logger log = LoggerFactory.getLogger(optController.class);

	@Autowired
	private SmppSession session;
	@Autowired
	private ApplicationProperties properties;
	
	@GetMapping("/sendmsg")
	public String sendmsg() {
		sendTextMessage(session, "3299", "Hello World", "<replace>");

		return "dd" ;
	}

	
	public SmppSessionConfiguration sessionConfiguration(ApplicationProperties properties) {
		SmppSessionConfiguration sessionConfig = new SmppSessionConfiguration();
		sessionConfig.setName("smpp.session");
		sessionConfig.setInterfaceVersion(SmppConstants.VERSION_3_4);
		sessionConfig.setType(SmppBindType.TRANSCEIVER);
		sessionConfig.setHost("smscsim.smpp.org");
		sessionConfig.setPort(2775);
		sessionConfig.setSystemId("iRj1AuCzRqGuIB2");
		sessionConfig.setPassword("LjAjaO5q");
		sessionConfig.setSystemType(null);
		sessionConfig.getLoggingOptions().setLogBytes(false);
		sessionConfig.getLoggingOptions().setLogPdu(true);

		return sessionConfig;
	}
	
	@Bean(destroyMethod = "destroy")
	public SmppSession session(ApplicationProperties properties) throws SmppBindException, SmppTimeoutException,
			SmppChannelException, UnrecoverablePduException, InterruptedException {
		SmppSessionConfiguration config = sessionConfiguration(properties);
		SmppSession session = clientBootstrap(properties).bind(config, new com.example.demo.ClientSmppSessionHandler(properties));

		return session;
	}

	public SmppClient clientBootstrap(ApplicationProperties properties) {
		return new DefaultSmppClient(Executors.newCachedThreadPool(), properties.getAsync().getSmppSessionSize());
	}

	private void sendTextMessage(SmppSession session, String sourceAddress, String message, String destinationAddress) {
		if (session.isBound()) {
			try {
				boolean requestDlr = true;
				SubmitSm submit = new SubmitSm();
				byte[] textBytes;
				textBytes = CharsetUtil.encode(message, CharsetUtil.CHARSET_ISO_8859_1);
				submit.setDataCoding(SmppConstants.DATA_CODING_LATIN1);
				if (requestDlr) {
					submit.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);
				}

				if (textBytes != null && textBytes.length > 255) {
					submit.addOptionalParameter(
							new Tlv(SmppConstants.TAG_MESSAGE_PAYLOAD, textBytes, "message_payload"));
				} else {
					submit.setShortMessage(textBytes);
				}

				submit.setSourceAddress(new Address((byte) 0x05, (byte) 0x01, sourceAddress));
				submit.setDestAddress(new Address((byte) 0x01, (byte) 0x01, destinationAddress));
				SubmitSmResp submitResponse = session.submit(submit, 10000);
				if (submitResponse.getCommandStatus() == SmppConstants.STATUS_OK) {
					log.info("SMS submitted, message id {}", submitResponse.getMessageId());
				} else {
					throw new IllegalStateException(submitResponse.getResultMessage());
				}
			} catch (RecoverablePduException | UnrecoverablePduException | SmppTimeoutException | SmppChannelException
					| InterruptedException e) {
				throw new IllegalStateException(e);
			}
			return;
		}
		throw new IllegalStateException("SMPP session is not connected");
	}

	//@Scheduled(initialDelayString = "${sms.async.initial-delay}", fixedDelayString = "${sms.async.initial-delay}")
	void enquireLinkJob() {
		if (session.isBound()) {
			try {
				log.info("sending enquire_link");
				EnquireLinkResp enquireLinkResp = session.enquireLink(new EnquireLink(),
						properties.getAsync().getTimeout());
				log.info("enquire_link_resp: {}", enquireLinkResp);
			} catch (SmppTimeoutException e) {
				log.info("Enquire link failed, executing reconnect; " + e);
				log.error("", e);
			} catch (SmppChannelException e) {
				log.info("Enquire link failed, executing reconnect; " + e);
				log.warn("", e);
			} catch (InterruptedException e) {
				log.info("Enquire link interrupted, probably killed by reconnecting");
			} catch (Exception e) {
				log.error("Enquire link failed, executing reconnect", e);
			}
		} else {
			log.error("enquire link running while session is not connected");
		}
	}
	
}
