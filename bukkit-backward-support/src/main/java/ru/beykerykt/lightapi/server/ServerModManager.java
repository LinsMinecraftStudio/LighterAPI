/**
 * The MIT License (MIT)
 *
 * <p>Copyright (c) 2015 Vladimir Mikhailov <beykerykt@gmail.com> Copyright (c) 2016-2017 The
 * ImplexDevOne Project
 *
 * <p>Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * <p>The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * <p>THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ru.beykerykt.lightapi.server;

import java.lang.reflect.InvocationTargetException;

import ru.beykerykt.lightapi.server.exceptions.UnknownModImplementationException;
import ru.beykerykt.lightapi.server.exceptions.UnknownNMSVersionException;
import ru.beykerykt.lightapi.server.nms.INMSHandler;

@Deprecated
public class ServerModManager {

    public static void init()
            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
            NoSuchMethodException, SecurityException, UnknownNMSVersionException, UnknownModImplementationException {
        throw new UnknownModImplementationException("UNKNOWN");
    }

    public static void shutdown() {
    }

    public static boolean isInitialized() {
        return false;
    }

    public static boolean registerServerMod(ServerModInfo info) {
        return false;
    }

    public static boolean unregisterServerMod(String modName) {
        return false;
    }

    public static INMSHandler getNMSHandler() {
        return null;
    }
}
