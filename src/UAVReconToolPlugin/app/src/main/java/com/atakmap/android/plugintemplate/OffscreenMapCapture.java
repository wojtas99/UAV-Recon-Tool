package com.atakmap.android.plugintemplate;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.atakmap.android.maps.MapView;
import com.atakmap.map.opengl.GLMapView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

public class OffscreenMapCapture {

    final float[] squareVertices = {
            1.0f, 1.0f,
            -1.0f, 1.0f,
            1.0f, -1.0f,
            -1.0f, -1.0f
    };

    GLSurfaceView glView;
    final int[] prevFrameBuffer = new int[1];
    final int[] colorRenderBuffer = new int[1];
    final int[] textureFrameBuffer = new int[1];
    final int[] textures = new int[1];
    boolean isGettingAndSettingImages = false;

    FloatBuffer imageVertexBuffer = null;

    GLES20Renderer renderer = null;

    final Runnable stopGettingAndSettingImage = new Runnable() {
        public void run() {
            GLES20.glDeleteTextures(1, textures, 0);
            GLES20.glDeleteRenderbuffers(1, colorRenderBuffer, 0);
            GLES20.glDeleteFramebuffers(1, textureFrameBuffer, 0);
            imageVertexBuffer = null;
            textures[0] = 0;
            colorRenderBuffer[0] = 0;
            textureFrameBuffer[0] = 0;
        }
    };

    final Runnable getAndSetImage = new Runnable() {
        boolean glFlushErrors(String msg, boolean quiet) {
            boolean r = false;
            if (!quiet) System.out.println("*** " + msg);
            while (true) {
                int err = GLES20.glGetError();
                if (err == GLES20.GL_NO_ERROR) break;
                if (!quiet) System.out.println("err " + Integer.toString(err, 16));
                r = true;
            }
            return r;
        }

        private boolean initializeFBO(int mapWidth, int mapHeight) {
            glFlushErrors(null, true);
            GLES20.glGenFramebuffers(1, textureFrameBuffer, 0);
            glFlushErrors("glGenFramebuffers", false);
            GLES20.glGenRenderbuffers(1, colorRenderBuffer, 0);
            glFlushErrors("glGenRenderbuffers", false);
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
                    mapWidth, mapHeight, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, null);
            glFlushErrors("offscreen texture create", false);
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, colorRenderBuffer[0]);
            glFlushErrors("glBindRenderbuffer", false);
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER,
                    GLES20.GL_DEPTH_COMPONENT16,
                    _nextPowerOf2(mapWidth), _nextPowerOf2(mapHeight));
            glFlushErrors("glRenderbufferStorage", false);
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
            glFlushErrors("glBindRenderbuffer", false);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureFrameBuffer[0]);
            glFlushErrors("glBindFramebuffer", false);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, textures[0], 0);
            glFlushErrors("glFramebufferTexture2D", false);
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_DEPTH_ATTACHMENT,
                    GLES20.GL_RENDERBUFFER, colorRenderBuffer[0]);
            glFlushErrors("glFramebufferRenderbuffer", false);
            int fboStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            return (fboStatus == GLES20.GL_FRAMEBUFFER_COMPLETE);
        }

        public void run() {
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFrameBuffer, 0);
            GLMapView glMapView = MapView.getMapView().getGLSurface().getGLMapView();
            int mapWidth = glMapView.getRight() - glMapView.getLeft();
            int mapHeight = glMapView.getTop() - glMapView.getBottom();
            float[] viewPort = new float[4];
            GLES20.glGetFloatv(GLES20.GL_VIEWPORT, viewPort, 0);
            synchronized (textures) {
                if (textureFrameBuffer[0] == 0) {
                    initializeFBO(mapWidth, mapHeight);
                } else {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureFrameBuffer[0]);
                }
                GLES20.glViewport(0, 0, mapWidth, mapHeight);
                // Rysujemy mapę do FBO bez czyszczenia
                MapView.getMapView().getGLSurface().getGLMapView().render();
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFrameBuffer[0]);
            }
            GLES20.glViewport((int)viewPort[0], (int)viewPort[1], (int)viewPort[2], (int)viewPort[3]);
            // Po aktualizacji tekstury prosimy GLSurfaceView o render
            glView.requestRender();
            refreshQueued = false;
        }
    };

    Thread offscreenRefreshThread;
    boolean refreshQueued;
    Bitmap img = null;
    int prog = -1;
    public static final int COORDS_PER_VERTEX = 2;
    public static final int vertexStride = COORDS_PER_VERTEX * 4;

    public class GLES20Renderer implements GLSurfaceView.Renderer {
        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0f,0f,0f,0f);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {}

        @Override
        public void onDrawFrame(GL10 gl) {
            if (imageVertexBuffer == null) {
                ByteBuffer bb = ByteBuffer.allocateDirect(squareVertices.length * 4);
                bb.order(ByteOrder.nativeOrder());
                imageVertexBuffer = bb.asFloatBuffer();
                imageVertexBuffer.put(squareVertices);
                imageVertexBuffer.position(0);
            }
            if (prog < 0) prog = ShaderInfo.loadImageShader();
            GLES20.glUseProgram(prog);

            float viewSizeX = glView.getWidth();
            float viewSizeY = glView.getHeight();
            float imgratio = viewSizeX / viewSizeY;
            float plw = 2f * viewSizeX, plh = 2f * viewSizeY;
            float tmpS = plw / imgratio;
            float tmpSize = (tmpS < plh) ? tmpS : plh;
            float dw = tmpSize * imgratio;
            float dh = tmpSize;
            float fwidth = 0.5f * dw / viewSizeX;
            float fheight = 0.5f * dh / viewSizeY;

            int locXYS = GLES20.glGetUniformLocation(prog, "xyscale");
            GLES20.glUniform2f(locXYS, fwidth, fheight);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(prog, "center"), 0f,0f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "rot"), (float)(-Math.PI/2f));
            int noA = GLES20.glGetUniformLocation(prog, "no_alpha");
            if (noA>=0) GLES20.glUniform1f(noA,0f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "alpha"),1f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "multip"),1f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "addv"),0f);

            int posHandle = GLES20.glGetAttribLocation(prog, "position");
            GLES20.glEnableVertexAttribArray(posHandle);
            GLES20.glVertexAttribPointer(posHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, imageVertexBuffer);
            int texHandle = GLES20.glGetUniformLocation(prog, "s_texture");
            GLES20.glUniform1i(texHandle,0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

            synchronized(textures) {
                if (textures[0]!=0) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
                }
            }
            GLES20.glDisableVertexAttribArray(posHandle);
        }
    }

    public OffscreenMapCapture(LinearLayout ll) {
        final EGLContext[] shared_ctx = new EGLContext[1];
        MapView.getMapView().getGLSurface().queueEvent(() -> {
            synchronized(shared_ctx){
                EGL egl = EGLContext.getEGL();
                shared_ctx[0] = ((EGL10)egl).eglGetCurrentContext();
                shared_ctx.notify();
            }
        });
        synchronized(shared_ctx) {
            if (shared_ctx[0]==null) {
                try { shared_ctx.wait(); } catch (InterruptedException ignored){}
            }
        }
        if (shared_ctx[0]==null) shared_ctx[0] = EGL10.EGL_NO_CONTEXT;

        glView = new GLSurfaceView(ll.getContext());
        // transparent RGBA config
        glView.setEGLConfigChooser(8,8,8,8,16,0);
        glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        glView.setZOrderMediaOverlay(true);

        // Context factory
        glView.setEGLContextFactory(new GLSurfaceView.EGLContextFactory(){
            @Override public void destroyContext(EGL10 egl, EGLDisplay d, EGLContext c){ egl.eglDestroyContext(d,c); }
            @Override public EGLContext createContext(EGL10 egl, EGLDisplay d, EGLConfig cfg){
                final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
                int[] attribs = { EGL_CONTEXT_CLIENT_VERSION,2, EGL10.EGL_NONE };
                return egl.eglCreateContext(d,cfg, shared_ctx[0], attribs);
            }
        });

        // renderer and render mode
        glView.setRenderer(renderer = new GLES20Renderer());
        glView.setPreserveEGLContextOnPause(true);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glView.setLayoutParams(lp);
        ll.addView(glView);
    }

    public void capture(boolean capture) {
        isGettingAndSettingImages = capture;
        if (capture) {
            refreshQueued = false;
            offscreenRefreshThread = new Thread(() -> {
                final long interval = 30L;
                while (Thread.currentThread()==offscreenRefreshThread) {
                    try { Thread.sleep(interval); } catch (InterruptedException ignored){}
                    if (isGettingAndSettingImages && !refreshQueued) {
                        refreshQueued = true;
                        MapView.getMapView().getGLSurface().queueEvent(getAndSetImage);
                    }
                }
            });
            offscreenRefreshThread.setPriority(Thread.NORM_PRIORITY);
            offscreenRefreshThread.setDaemon(true);
            offscreenRefreshThread.start();
        } else {
            offscreenRefreshThread = null;
            MapView.getMapView().getGLSurface().queueEvent(stopGettingAndSettingImage);
        }
    }

    private static int _nextPowerOf2(int value) {
        --value; value=(value>>1)|value; value=(value>>2)|value;
        value=(value>>4)|value; value=(value>>8)|value; value=(value>>16)|value; ++value;
        return value;
    }
}
