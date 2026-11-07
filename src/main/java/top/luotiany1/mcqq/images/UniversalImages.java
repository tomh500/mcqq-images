package top.luotiany1.mcqq.images;

import java.io.*;
import java.lang.reflect.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Universal Java 21 implementation with a generated per-version name bridge. */
public final class UniversalImages implements ClientModInitializer {
    private static final String MC="net.minecraft.client.Minecraft", COMPONENT="net.minecraft.network.chat.Component";
    private final LinkedHashMap<String,Preview> previews=new LinkedHashMap<>();
    private final ExecutorService workers=Executors.newFixedThreadPool(2,r -> { Thread t=new Thread(r,"MCQQ image download"); t.setDaemon(true); return t; });
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private Bridge bridge;
    private Object pipeline;
    private boolean renderError;
    static final class Preview {
        final String caption; final long created=System.currentTimeMillis();
        final List<Object> frames=new ArrayList<>(); final List<Integer> delays=new ArrayList<>();
        int width,height; boolean failed;
        Preview(String caption) { this.caption=caption; }
        Object frame(long time) {
            int total=delays.stream().mapToInt(Integer::intValue).sum(); long offset=Math.floorMod(time-created,Math.max(1,total));
            for(int i=0;i<delays.size();i++) { offset-=delays.get(i); if(offset<0)return frames.get(i); }
            return frames.getFirst();
        }
    }
    @Override public void onInitializeClient() {
        String version=FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        try {
            bridge=new Bridge(version);
            // Resolve essential members before hooking events: a bad adapter cannot
            // break incoming chat or repeatedly throw on the render thread.
            bridge.type(COMPONENT); pipeline=bridge.pipeline();
            event("net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents","GAME",args -> {
                if(!Boolean.TRUE.equals(args[1]) && bridge.text(args[0]).contains("[群内]")) inspect(args[0],bridge.text(args[0]));
            });
            event("net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents","DISCONNECT",args -> clear());
            hud();
            System.out.println("[MCQQ Images] Universal adapter loaded for "+version);
        } catch(Exception|LinkageError error) {
            System.err.println("[MCQQ Images] Cannot initialize "+version+"; original chat links remain usable."); error.printStackTrace();
        }
    }
    @FunctionalInterface interface Callback { void call(Object[] args) throws Exception; }
    static Object callback(Class<?> type,Callback body) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                case "toString" -> "MCQQ callback"; case "hashCode" -> System.identityHashCode(proxy); case "equals" -> proxy==args[0]; default -> null;
            };
            try { body.call(args); } catch(Exception error) { System.err.println("[MCQQ Images] Event failed: "+error); }
            return null;
        });
    }
    static void event(String owner,String field,Callback body) throws Exception {
        Object event=Class.forName(owner).getField(field).get(null);
        Class<?> eventType=Class.forName("net.fabricmc.fabric.api.event.Event");
        Object invoker=eventType.getMethod("invoker").invoke(event);
        Class<?> callbackType=invoker.getClass().getInterfaces()[0];
        eventType.getMethod("register",Object.class).invoke(event,callback(callbackType,body));
    }
    private void hud() throws Exception {
        try {
            Class<?> registry=Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
            Object chat=Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements").getField("CHAT").get(null);
            for(Method method:registry.getMethods()) if(method.getName().equals("attachElementBefore") && method.getParameterCount()==3) {
                method.invoke(null,chat,bridge.id("previews"),callback(method.getParameterTypes()[2],args -> render(args[0]))); return;
            }
            throw new NoSuchMethodException("HudElementRegistry.attachElementBefore");
        } catch(ClassNotFoundException absent) {
            event("net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback","EVENT",args -> render(args[0]));
        }
    }
    private void inspect(Object component,String caption) throws Exception {
        Object style=bridge.call(component,COMPONENT,"getStyle");
        Object click=bridge.call(style,"net.minecraft.network.chat.Style","getClickEvent");
        String label=bridge.text(component);
        if(click!=null && (label.contains("[图片]") || label.contains("[表情包]"))) {
            URI uri=null;
            if(bridge.version.equals("1.21.4")) {
                Object action=bridge.call(click,"net.minecraft.network.chat.ClickEvent","getAction");
                if(action==bridge.field(null,"net.minecraft.network.chat.ClickEvent$Action","OPEN_URL"))
                    uri=URI.create((String)bridge.call(click,"net.minecraft.network.chat.ClickEvent","getValue"));
            } else if(bridge.type("net.minecraft.network.chat.ClickEvent$OpenUrl").isInstance(click))
                uri=(URI)bridge.call(click,"net.minecraft.network.chat.ClickEvent$OpenUrl","uri");
            if(uri!=null && allowed(uri) && !previews.containsKey(uri.toString())) {
                while(previews.size()>=6) evict(previews.keySet().iterator().next());
                Preview preview=new Preview(caption.codePoints().limit(45).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString());
                previews.put(uri.toString(),preview); URI source=uri; workers.execute(()->load(source,preview));
            }
        }
        for(Object child:(List<?>)bridge.call(component,COMPONENT,"getSiblings")) inspect(child,caption);
    }
    static boolean allowed(URI uri) {
        String host=uri.getHost(); if(host==null || uri.getUserInfo()!=null || uri.getFragment()!=null)return false;
        host=host.toLowerCase(Locale.ROOT);
        if(host.equals("lemoe.luotiany1.top") && uri.getPath().matches("/mcqq-media/[a-f0-9]{32}\\.(png|jpg|gif|webp)"))
            return ("http".equals(uri.getScheme()) && uri.getPort()==18766) || ("https".equals(uri.getScheme()) && (uri.getPort()==-1 || uri.getPort()==443));
        if(!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))return false;
        if(uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443)return false;
        for(String domain:List.of("qq.com","qq.com.cn","qpic.cn","gtimg.cn","gtimg.com","idqqimg.com","qlogo.cn"))
            if(host.equals(domain) || host.endsWith('.'+domain))return true;
        return false;
    }
    private void load(URI uri,Preview preview) {
        try {
            byte[] data=null;
            for(int i=0;i<4;i++) {
                if(!allowed(uri))throw new IOException("Image host rejected");
                HttpResponse<InputStream> response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(InputStream body=response.body()) {
                    if(response.statusCode()>=300 && response.statusCode()<400) { uri=uri.resolve(response.headers().firstValue("location").orElseThrow()); continue; }
                    if(response.statusCode()!=200)throw new IOException("Image unavailable");
                    data=body.readNBytes(8*1024*1024+1); if(data.length>8*1024*1024)throw new IOException("Image too large"); break;
                }
            }
            if(data==null)throw new IOException("Too many redirects");
            var decoded=ImageFrames.decode(data);
            // Encode bounded thumbnails off the render thread; NativeImage.read
            // handles each version's ARGB/ABGR representation without per-pixel reflection.
            List<byte[]> pngs=new ArrayList<>();
            for(var frame:decoded) { ByteArrayOutputStream output=new ByteArrayOutputStream(); ImageIO.write(frame.image(),"PNG",output); pngs.add(output.toByteArray()); }
            bridge.execute(()-> {
                if(!previews.containsValue(preview))return;
                try {
                    Object manager=bridge.call(bridge.client(),MC,"getTextureManager");
                    for(int i=0;i<decoded.size();i++) {
                        Object nativeImage=bridge.call(null,"com.mojang.blaze3d.platform.NativeImage","read",pngs.get(i));
                        Object texture=null;
                        try {
                            texture=bridge.texture(nativeImage);
                            Object id=bridge.id("image/"+UUID.randomUUID());
                            bridge.call(manager,"net.minecraft.client.renderer.texture.TextureManager","register",id,texture);
                            preview.frames.add(id); preview.delays.add(decoded.get(i).milliseconds());
                        } catch(Exception error) {
                            if(texture instanceof AutoCloseable close) close.close();
                            else if(nativeImage instanceof AutoCloseable close) close.close();
                            throw error;
                        }
                    }
                    preview.width=decoded.getFirst().image().getWidth(); preview.height=decoded.getFirst().image().getHeight();
                } catch(Exception error) { preview.failed=true; System.err.println("[MCQQ Images] Texture upload failed: "+error); }
            });
        } catch(Exception error) { try { bridge.execute(()->preview.failed=true); } catch(Exception ignored) {} }
    }
    private void render(Object graphics) throws Exception {
        if(renderError)return;
        try {
            if(bridge.field(bridge.client(),MC,"level")==null || bridge.hidden())return;
            long now=System.currentTimeMillis();
            for(String key:previews.entrySet().stream().filter(e->now-e.getValue().created>=3_600_000).map(Map.Entry::getKey).toList())evict(key);
            String gc=bridge.graphicsClass(); int width=(int)bridge.call(graphics,gc,"guiWidth"),height=(int)bridge.call(graphics,gc,"guiHeight");
            int y=24,shown=0,boxWidth=Math.min(170,width/3),x=width-boxWidth-8;
            Object font=bridge.field(bridge.client(),MC,"font"); var entries=new ArrayList<>(previews.values()); Collections.reverse(entries);
            for(Preview preview:entries) {
                if(shown>=3 || y+35>=height)break;
                if(now-preview.created>30_000 && bridge.screen()==null)continue;
                int w=boxWidth-8,h=preview.height==0?25:Math.max(1,Math.min(95,(int)(w*(double)preview.height/preview.width)));
                w=preview.width==0?w:Math.max(1,Math.min(w,(int)(h*(double)preview.width/preview.height)));
                bridge.call(graphics,gc,"fill",x-3,y-3,x+boxWidth+3,y+h+17,0xBB101820);
                String caption=(String)bridge.call(font,"net.minecraft.client.gui.Font","plainSubstrByWidth",preview.caption,boxWidth-4);
                text(graphics,font,caption,x,y,0xFFFFFFFF);
                if(!preview.frames.isEmpty() && !preview.failed)
                    bridge.call(graphics,gc,"blit",pipeline,preview.frame(now),x,y+12,0f,0f,w,h,preview.width,preview.height,preview.width,preview.height);
                else text(graphics,font,preview.failed?"图片不可用，点击聊天链接打开":"正在加载图片…",x,y+14,0xFFAAAAAA);
                y+=h+24; shown++;
            }
        } catch(Exception|LinkageError error) { renderError=true; clear(); System.err.println("[MCQQ Images] Rendering disabled; chat links remain usable: "+error); }
    }
    private void text(Object graphics,Object font,String text,int x,int y,int color) throws Exception {
        bridge.call(graphics,bridge.graphicsClass(),bridge.modern?"text":"drawString",font,text,x,y,color);
    }
    private void evict(String key) throws Exception {
        Preview removed=previews.remove(key); if(removed==null)return;
        Object manager=bridge.call(bridge.client(),MC,"getTextureManager");
        for(Object id:removed.frames)bridge.call(manager,"net.minecraft.client.renderer.texture.TextureManager","release",id);
    }
    private void clear() throws Exception { for(String key:List.copyOf(previews.keySet()))evict(key); }
}
