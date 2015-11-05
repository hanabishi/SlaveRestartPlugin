package support;

import hudson.model.TaskListener;

import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import slavemonitor.SlaveWatcher;

public class LogHandler {
    public static String DEFAULT_SENDER = "ciemail@buildings.schneider-electric.com";

    public static LinkedList<Address> createReceivers() throws AddressException {
        LinkedList<Address> list = new LinkedList<Address>();
        list.add(new InternetAddress("marcus.jacobsson@schneider-electric.com"));
        return list;
    }

    private static String generateStackString(final Exception e) {
        String stack = "";
        for (final StackTraceElement elem : e.getStackTrace()) {
            stack = stack.concat("* " + elem.getFileName() + "in " + elem.getClassName() + "(" + elem.getMethodName()
                    + ")" + "Line: " + elem.getLineNumber() + "\n");
        }
        return stack;
    }

    public static void printStackTrace(SlaveWatcher slave, Exception e) {
        logPrint(slave.getName(), e.getMessage() + "\n" + LogHandler.generateStackString(e));
        e.printStackTrace();
    }

    public static void printStackTrace(final Exception e) {
        logPrint("", e.getMessage() + "\n" + LogHandler.generateStackString(e));
        e.printStackTrace();
    }

    private static void logPrint(String slaveName, String message) {
        String localhostname = "";
        try {
            localhostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {}

        sendMail(TaskListener.NULL, "[SBO-JENKINS@" + localhostname + "] Stacktrace"
                + ((slaveName.isEmpty()) ? "" : " for slave " + slaveName), "Stacktrace: " + message);
    }

    public static boolean sendMail(final TaskListener listener, final String subjectMessage, final String body) {
        try {
            final String host = "10.158.9.6";
            final Properties properties = System.getProperties();
            properties.setProperty("mail.smtp.host", host);
            properties.put("mail.smtp.password", "Not4sale!");
            properties.put("mail.smtp.user", "ciemail");
            final Session session = Session.getDefaultInstance(properties);
            final MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(LogHandler.DEFAULT_SENDER));
            RecipientType recType = Message.RecipientType.TO;

            for (Address a : LogHandler.createReceivers()) {
                message.addRecipient(recType, a);
            }

            message.setSubject(subjectMessage);
            Address[] reply = new Address[1];
            reply[0] = new InternetAddress("marcus.jacobsson@schneider-electric.com");
            message.setReplyTo(reply);
            message.setText(body);
            Transport.send(message);
            return true;
        } catch (final AddressException e) {
            if (listener != null) {
                final String stringToPrint = "[MAIL-ERROR] LogHandler-sendMail: " + e.getMessage() + ","
                        + LogHandler.generateStackString(e);
                listener.getLogger().println(stringToPrint);
            }
            e.printStackTrace();
            return false;
        } catch (final MessagingException e) {
            if (listener != null) {
                final String stringToPrint = "[MAIL-ERROR] LogHandler-sendMail: " + e.getMessage() + ","
                        + LogHandler.generateStackString(e);
                listener.getLogger().println(stringToPrint);
            }
            e.printStackTrace();
            return false;
        }
    }

}
