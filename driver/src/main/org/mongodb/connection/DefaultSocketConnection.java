/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.connection;

import org.bson.ByteBuf;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

// TODO: migrate all the DBPort configuration
class DefaultSocketConnection extends DefaultConnection {
    private final SocketFactory socketFactory;
    private volatile Socket socket;

    public DefaultSocketConnection(final ServerAddress address, final DefaultConnectionSettings settings,
                                   final BufferProvider bufferProvider, final SocketFactory socketFactory) {
        super(address, settings, bufferProvider);
        this.socketFactory = socketFactory;
    }

    protected void ensureOpen() {
        try {
            if (socket == null) {
                socket = socketFactory.createSocket();
                initializeSocket(socket);
            }
        } catch (IOException e) {
            close();
            throw new MongoSocketOpenException("Exception opening socket", getServerAddress(), e);
        }
    }

    @Override
    protected void sendOneWayMessage(final List<ByteBuf> byteBufList) throws IOException {
        for (ByteBuf cur : byteBufList) {
            socket.getOutputStream().write(cur.array(), 0, cur.limit());
        }
    }

    protected void fillAndFlipBuffer(final ByteBuf buffer) throws IOException {
        int totalBytesRead = 0;
        byte[] bytes = buffer.array();
        while (totalBytesRead < buffer.limit()) {
            final int bytesRead = socket.getInputStream().read(bytes, totalBytesRead, buffer.limit() - totalBytesRead);
            if (bytesRead == -1) {
                throw new MongoSocketReadException("Prematurely reached end of stream", getServerAddress());
            }
            totalBytesRead += bytesRead;
        }
    }

    //CHECKSTYLE:OFF
    public void close() {
        try {
            super.close();
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) { // NOPMD
            // ignore
        }
    }
}