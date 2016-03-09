/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/
package org.apache.qpid.proton.engine.impl;

import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.WebSocket;
import org.apache.qpid.proton.engine.WebSocketHandler;
import org.apache.qpid.proton.engine.WebSocketHeader;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WebSocketImplTest
{
    private String _hostName = "host_XXX";
    private String _webSocketPath = "path1/path2";
    private int _webSocketPort = 1234567890;
    private String _webSocketProtocol = "subprotocol_name";
    private Map<String, String> _additionalHeaders = new HashMap<String, String>();

    private void init()
    {
        _additionalHeaders.put("header1", "content1");
        _additionalHeaders.put("header2", "content2");
        _additionalHeaders.put("header3", "content3");
    }

    @Test
    public void testConstructor()
    {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);

        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        assertNotNull(inputBuffer);
        assertNotNull(outputBuffer);
        assertNotNull(pingBuffer);

        assertEquals(inputBuffer.capacity(), WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        assertEquals(outputBuffer.capacity(), WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        assertEquals(pingBuffer.capacity(), WebSocketHeader.PAYLOAD_MEDIUM_MAX);

        assertFalse(webSocketImpl.getEnabled());
    }

    @Test
    public void testConfigure_handler_null()
    {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);

        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                null
        );

        assertNotNull(webSocketImpl.getWebSocketHandler());
        assertTrue(webSocketImpl.getEnabled());
    }

    @Test
    public void testConfigure_handler_not_null()
    {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        WebSocketHandler webSocketHandler = new WebSocketHandlerImpl();

        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                webSocketHandler
        );

        assertEquals(webSocketHandler, webSocketImpl.getWebSocketHandler());
        assertTrue(webSocketImpl.getEnabled());
    }

    @Test
    public void testWriteUpgradeRequest()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl spyWebSocketHandler = spy(webSocketHandler);

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                spyWebSocketHandler
        );
        webSocketImpl.writeUpgradeRequest();

        verify(spyWebSocketHandler, times(1)).createUpgradeRequest(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders
        );

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.flip();

        assertTrue(outputBuffer.remaining() == 268);
    }

    @Test
    public void testWritePong()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl spyWebSocketHandler = spy(webSocketHandler);

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                spyWebSocketHandler
        );

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        webSocketImpl.writePong();

        verify(spyWebSocketHandler, times(1)).createPong(pingBuffer, outputBuffer);
    }

    @Test
    public void testWrap_creates_sniffer()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                webSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertNotNull(transportWrapper);
    }

    @Test
    public void testWrapBuffer_enabled()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                mockWebSocketHandler
        );

        ByteBuffer srcBuffer = ByteBuffer.allocate(50);
        srcBuffer.clear();
        ByteBuffer dstBuffer = ByteBuffer.allocate(50);

        webSocketImpl._isWebSocketEnabled = true;
        webSocketImpl.wrapBuffer(srcBuffer, dstBuffer);

        verify(mockWebSocketHandler, times(1)).wrapBuffer(srcBuffer, dstBuffer);
    }

    @Test
    public void testWrapBuffer_not_enabled()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                mockWebSocketHandler
        );

        ByteBuffer srcBuffer = ByteBuffer.allocate(25);
        srcBuffer.clear();
        srcBuffer.put("abcdefghijklmnopqrstvwxyz".getBytes());
        srcBuffer.flip();
        ByteBuffer dstBuffer = ByteBuffer.allocate(25);
        dstBuffer.put("1234567890".getBytes());

        webSocketImpl._isWebSocketEnabled = false;
        webSocketImpl.wrapBuffer(srcBuffer, dstBuffer);

        dstBuffer.flip();
        assertTrue(Arrays.equals(srcBuffer.array(), dstBuffer.array()));
        verify(mockWebSocketHandler, times(0)).wrapBuffer((ByteBuffer) any(),(ByteBuffer) any());
    }

    @Test
    public void testPending_state_notStarted()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                webSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);
        transportWrapper.pending();

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.flip();

        assertTrue(outputBuffer.remaining() == 268);
    }

    @Test
    public void testPending_state_notStarted__output_not_empty()
    {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                webSocketHandler
        );

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.put(message.getBytes());

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(message.length() == transportWrapper.pending());

        ByteBuffer actual = webSocketImpl.getOutputBuffer();
        assertTrue(Arrays.equals(outputBuffer.array(), actual.array()));
    }

    @Test
    public void testPending_state_notStarted__head_closed()
    {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                webSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        transportWrapper.close_tail();
        assertTrue(transportWrapper.pending() == Transport.END_OF_STREAM);
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_FAILED );

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.flip();

        assertTrue(outputBuffer.remaining() == 268);
    }

    @Test
    public void testPending_state_connecting()
    {
        init();

        WebSocketImpl webSocketImpl = webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl spyWebSocketHandler = spy(webSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders, webSocketHandler        );

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.flip();

        transportWrapper.pending();
        assertTrue(outputBuffer.remaining() == 268);
    }

    @Test
    public void testPending_state_connecting_head_closed_empty_buffer()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                webSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.clear();
        transportWrapper.close_tail();

        assertTrue(transportWrapper.pending() == Transport.END_OF_STREAM);
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_FAILED );
    }

    @Test
    public void testProcess_state_not_started()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                webSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);

        verify(mockTransportInput, times(1)).process();
    }

    @Test
    public void testProcess_state_changes_from_connecting_to_flow_on_valid_reply()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                mockWebSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler.createUpgradeRequest(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders)).thenReturn("Request");

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
    }

    @Test
    public void testPending_state_flow_calls_wrapBuffer()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                mockWebSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler.createUpgradeRequest(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders)).thenReturn("Request");

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.put(message.getBytes());
        when(mockTransportOutput.pending()).thenReturn(100);
        transportWrapper.pending();

        verify(mockWebSocketHandler, times(1)).wrapBuffer((ByteBuffer) any(),(ByteBuffer) any());
    }

    @Test
    public void testPending_state_flow_empty_output()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                mockWebSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler.createUpgradeRequest(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders)).thenReturn("Request");

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.put(message.getBytes());
        when(mockTransportOutput.pending()).thenReturn(0);

        assertEquals(transportWrapper.pending(), 0);
        verify(mockWebSocketHandler, times(0)).wrapBuffer((ByteBuffer) any(),(ByteBuffer) any());
    }

    @Test
    public void testProcess_state_flow_repeated_reply()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders,
                mockWebSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler.createUpgradeRequest(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders)).thenReturn("Request");

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "HTTP ";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();
        verify(mockWebSocketHandler, times(0)).unwrapBuffer((ByteBuffer) any());
    }

    @Test
    public void testProcess_state_flow_calls_unwrap()
    {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl(WebSocketHeader.PAYLOAD_MEDIUM_MAX);
        webSocketImpl.configure(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders, mockWebSocketHandler
        );

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler.createUpgradeRequest(_hostName, _webSocketPath, _webSocketPort, _webSocketProtocol, _additionalHeaders)).thenReturn("Request");

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();
        verify(mockWebSocketHandler, times(1)).unwrapBuffer((ByteBuffer) any());
    }
}
