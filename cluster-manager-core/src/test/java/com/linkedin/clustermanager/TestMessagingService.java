package com.linkedin.clustermanager;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.linkedin.clustermanager.messaging.AsyncCallback;
import com.linkedin.clustermanager.messaging.handling.MessageHandler;
import com.linkedin.clustermanager.messaging.handling.MessageHandlerFactory;
import com.linkedin.clustermanager.model.Message;
import com.linkedin.clustermanager.model.Message.MessageType;
import com.linkedin.clustermanager.tools.ClusterStateVerifier;

public class TestMessagingService extends ZkStandAloneCMHandler
{
  public static class TestMessagingHandlerFactory implements
      MessageHandlerFactory
  {
    public static HashSet<String> _processedMsgIds = new HashSet<String>();

    @Override
    public MessageHandler createHandler(Message message,
        NotificationContext context)
    {
      return new TestMessagingHandler();
    }

    @Override
    public String getMessageType()
    {
      return "TestExtensibility";
    }

    @Override
    public void reset()
    {
      // TODO Auto-generated method stub

    }

    public static class TestMessagingHandler implements MessageHandler
    {
      @Override
      public void handleMessage(Message message, NotificationContext context,
          Map<String, String> resultMap) throws InterruptedException
      {
        // TODO Auto-generated method stub
        Thread.currentThread().sleep(1000);
        System.out.println("TestMessagingHandler " + message.getMsgId());
        _processedMsgIds.add(message.getRecord().getSimpleField(
            "TestMessagingPara"));
        resultMap.put("ReplyMessage", "TestReplyMessage");
      }
    }
  }

  @Test
  public void TestMessageSimpleSend() throws Exception
  {
    String hostSrc = "localhost_" + START_PORT;
    String hostDest = "localhost_" + (START_PORT + 1);

    TestMessagingHandlerFactory factory = new TestMessagingHandlerFactory();
    _managerMap.get(hostDest).getMessagingService()
        .registerMessageHandlerFactory(factory.getMessageType(), factory);

    String msgId = new UUID(123, 456).toString();
    Message msg = new Message(factory.getMessageType(),msgId);
    msg.setMsgId(msgId);
    msg.setSrcName(hostSrc);
    msg.setTgtSessionId("*");
    msg.setMsgState("new");
    String para = "Testing messaging para";
    msg.getRecord().setSimpleField("TestMessagingPara", para);

    Criteria cr = new Criteria();
    cr.setInstanceName(hostDest);
    cr.setRecipientInstanceType(InstanceType.PARTICIPANT);
    cr.setSessionSpecific(false);

    int nMsgs = _managerMap.get(hostSrc).getMessagingService().send(cr, msg);
    Assert.assertTrue(nMsgs == 1);
    Thread.sleep(2500);
    // Thread.currentThread().join();
    Assert.assertTrue(TestMessagingHandlerFactory._processedMsgIds
        .contains(para));

  }

  public static class MockAsyncCallback extends AsyncCallback
  {

    public MockAsyncCallback(long timeout)
    {
      super(timeout);
    }

    @Override
    public void onTimeOut()
    {
      // TODO Auto-generated method stub

    }

    @Override
    public void onReplyMessage(Message message)
    {
      // TODO Auto-generated method stub

    }

  }

  public static class TestAsyncCallback extends AsyncCallback
  {
    public TestAsyncCallback(long timeout)
    {
      super(timeout);
    }

    static HashSet<String> _replyedMessageContents = new HashSet<String>();
    public boolean timeout = false;

    @Override
    public void onTimeOut()
    {
      timeout = true;
    }

    @Override
    public void onReplyMessage(Message message)
    {
      // TODO Auto-generated method stub
      System.out.println("OnreplyMessage: "
          + message.getRecord()
              .getMapField(Message.Attributes.MESSAGE_RESULT.toString())
              .get("ReplyMessage"));
      _replyedMessageContents.add(message.getRecord()
          .getMapField(Message.Attributes.MESSAGE_RESULT.toString())
          .get("ReplyMessage"));
    }

  }

  @Test
  public void TestMessageSimpleSendReceiveAsync() throws Exception
  {
    String hostSrc = "localhost_" + START_PORT;
    String hostDest = "localhost_" + (START_PORT + 1);

    TestMessagingHandlerFactory factory = new TestMessagingHandlerFactory();
    _managerMap.get(hostDest).getMessagingService()
        .registerMessageHandlerFactory(factory.getMessageType(), factory);

    _managerMap.get(hostSrc).getMessagingService()
        .registerMessageHandlerFactory(factory.getMessageType(), factory);

    String msgId = new UUID(123, 456).toString();
    Message msg = new Message(factory.getMessageType(),msgId);
    msg.setMsgId(msgId);
    msg.setSrcName(hostSrc);

    msg.setTgtSessionId("*");
    msg.setMsgState("new");
    String para = "Testing messaging para";
    msg.getRecord().setSimpleField("TestMessagingPara", para);

    Criteria cr = new Criteria();
    cr.setInstanceName(hostDest);
    cr.setRecipientInstanceType(InstanceType.PARTICIPANT);
    cr.setSessionSpecific(false);

    TestAsyncCallback callback = new TestAsyncCallback(60000);

    _managerMap.get(hostSrc).getMessagingService().send(cr, msg, callback);

    Thread.sleep(2000);
    // Thread.currentThread().join();
    Assert.assertTrue(TestAsyncCallback._replyedMessageContents
        .contains("TestReplyMessage"));
    Assert.assertTrue(callback.getMessageReplied().size() == 1);

    TestAsyncCallback callback2 = new TestAsyncCallback(500);
    _managerMap.get(hostSrc).getMessagingService().send(cr, msg, callback2);

    Thread.sleep(3000);
    // Thread.currentThread().join();
    Assert.assertTrue(callback2.isTimedOut());

  }

  @Test
  public void TestBlockingSendReceive() throws Exception
  {
    String hostSrc = "localhost_" + START_PORT;
    String hostDest = "localhost_" + (START_PORT + 1);

    TestMessagingHandlerFactory factory = new TestMessagingHandlerFactory();
    _managerMap.get(hostDest).getMessagingService()
        .registerMessageHandlerFactory(factory.getMessageType(), factory);

    String msgId = new UUID(123, 456).toString();
    Message msg = new Message(factory.getMessageType(),msgId);
    msg.setMsgId(msgId);
    msg.setSrcName(hostSrc);

    msg.setTgtSessionId("*");
    msg.setMsgState("new");
    String para = "Testing messaging para";
    msg.getRecord().setSimpleField("TestMessagingPara", para);

    Criteria cr = new Criteria();
    cr.setInstanceName(hostDest);
    cr.setRecipientInstanceType(InstanceType.PARTICIPANT);
    cr.setSessionSpecific(false);

    AsyncCallback asyncCallback = new MockAsyncCallback(60000);
    int messagesSent = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, asyncCallback);

    Assert.assertTrue(asyncCallback.getMessageReplied().get(0).getRecord()
        .getMapField(Message.Attributes.MESSAGE_RESULT.toString())
        .get("ReplyMessage").equals("TestReplyMessage"));
    Assert.assertTrue(asyncCallback.getMessageReplied().size() == 1);

    
    AsyncCallback asyncCallback2 = new MockAsyncCallback(500);
    messagesSent = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, asyncCallback2);
    Assert.assertTrue(asyncCallback2.isTimedOut());

  }

  @Test
  public void TestMultiMessageCriteria() throws Exception
  {
    String hostSrc = "localhost_" + START_PORT;

    for (int i = 0; i < NODE_NR; i++)
    {
      TestMessagingHandlerFactory factory = new TestMessagingHandlerFactory();
      String hostDest = "localhost_" + (START_PORT + i);
      _managerMap.get(hostDest).getMessagingService()
          .registerMessageHandlerFactory(factory.getMessageType(), factory);
    }
    String msgId = new UUID(123, 456).toString();
    Message msg = new Message(
        new TestMessagingHandlerFactory().getMessageType(),msgId);
    msg.setMsgId(msgId);
    msg.setSrcName(hostSrc);

    msg.setTgtSessionId("*");
    msg.setMsgState("new");
    String para = "Testing messaging para";
    msg.getRecord().setSimpleField("TestMessagingPara", para);

    Criteria cr = new Criteria();
    cr.setInstanceName("*");
    cr.setRecipientInstanceType(InstanceType.PARTICIPANT);
    cr.setSessionSpecific(false);
    AsyncCallback callback1 = new MockAsyncCallback(2000);
    int messageSent1 = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, callback1);

    Assert.assertTrue(callback1.getMessageReplied().get(0).getRecord()
        .getMapField(Message.Attributes.MESSAGE_RESULT.toString())
        .get("ReplyMessage").equals("TestReplyMessage"));
    Assert.assertTrue(callback1.getMessageReplied().size() == NODE_NR);

    AsyncCallback callback2 = new MockAsyncCallback(500);
    int messageSent2 = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, callback2);
    Assert.assertTrue(callback2.isTimedOut());

    cr.setResourceKey("TestDB_17");
    AsyncCallback callback3 = new MockAsyncCallback(2000);
    int messageSent3 = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, callback3);
    Assert.assertTrue(callback3.getMessageReplied().size() == 3);

    cr.setResourceState("SLAVE");
    AsyncCallback callback4 = new MockAsyncCallback(2000);
    int messageSent4 = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, callback4);
    Assert.assertTrue(callback4.getMessageReplied().size() == 2);

  }

  @Test
  public void TestControllerMessage() throws Exception
  {
    String hostSrc = "localhost_" + START_PORT;

    for (int i = 0; i < NODE_NR; i++)
    {
      TestMessagingHandlerFactory factory = new TestMessagingHandlerFactory();
      String hostDest = "localhost_" + (START_PORT + i);
      _managerMap.get(hostDest).getMessagingService()
          .registerMessageHandlerFactory(factory.getMessageType(), factory);
    }
    String msgId = new UUID(123, 456).toString();
    Message msg = new Message(MessageType.CONTROLLER_MSG,msgId);
    msg.setMsgId(msgId);
    msg.setSrcName(hostSrc);

    msg.setTgtSessionId("*");
    msg.setMsgState("new");
    String para = "Testing messaging para";
    msg.getRecord().setSimpleField("TestMessagingPara", para);

    Criteria cr = new Criteria();
    cr.setInstanceName("*");
    cr.setRecipientInstanceType(InstanceType.CONTROLLER);
    cr.setSessionSpecific(false);

    AsyncCallback callback1 = new MockAsyncCallback(2000);
    int messagesSent = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, callback1);

    Assert.assertTrue(callback1.getMessageReplied().get(0).getRecord()
        .getMapField(Message.Attributes.MESSAGE_RESULT.toString())
        .get("ControllerResult").indexOf(msgId) != -1);
    Assert.assertTrue(callback1.getMessageReplied().size() == 1);

    msgId = UUID.randomUUID().toString();
    msg.setMsgId(msgId);
    cr.setResourceKey("TestDB_17");
    AsyncCallback callback2 = new MockAsyncCallback(2000);
    messagesSent = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, callback2);
    Assert.assertTrue(callback2.getMessageReplied().get(0).getRecord()
        .getMapField(Message.Attributes.MESSAGE_RESULT.toString())
        .get("ControllerResult").indexOf(msgId) != -1);

    Assert.assertTrue(callback2.getMessageReplied().size() == 1);

    msgId = UUID.randomUUID().toString();
    msg.setMsgId(msgId);
    cr.setResourceState("SLAVE");
    AsyncCallback callback3 = new MockAsyncCallback(2000);
    messagesSent = _managerMap.get(hostSrc).getMessagingService()
        .sendAndWait(cr, msg, callback3);
    Assert.assertTrue(callback3.getMessageReplied().get(0).getRecord()
        .getMapField(Message.Attributes.MESSAGE_RESULT.toString())
        .get("ControllerResult").indexOf(msgId) != -1);

    Assert.assertTrue(callback3.getMessageReplied().size() == 1);

  }
}