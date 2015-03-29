package io.quintus.bunjetty;

import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.http.HttpServlet;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class BunJetty extends Plugin {

    private static BunJetty plugin;
    private ServletContextHandler contextHandler;
    private Configuration config;
    private HashMap<HttpServlet, String> servlets;

    public static BunJetty getPlugin() {
        return plugin;
    }

    public Configuration getConfig() {
        return config;
    }

    @Override
    public void onEnable() {
        plugin = this;
        servlets = new HashMap<HttpServlet, String>();

        try {
            loadConfig();
        } catch (Exception e) {
            getLogger().log(Level.INFO, "Error loading config...");
            return;
        }

        registerServlet(this, new ExampleServlet());

        getExecutorService().submit(new Runnable() {
            public void run() {
                BunJetty plugin = BunJetty.getPlugin();
                Server server = new Server();
                ServerConnector serverConnector = new ServerConnector(server, plugin.getExecutorService(), null, null, -1, -1, new HttpConnectionFactory());
                serverConnector.setIdleTimeout(TimeUnit.HOURS.toMillis(1));
                serverConnector.setSoLingerTime(-1);
                serverConnector.setHost(plugin.getConfig().getString("host", "127.0.0.1"));
                serverConnector.setPort(plugin.getConfig().getInt("port", 7432));
                server.addConnector(serverConnector);

                plugin.contextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
                contextHandler.setContextPath("/");
                server.setHandler(plugin.contextHandler);

                plugin.initContextHandler();

                try {
                    server.start();
                    server.join();
                } catch (InterruptedException e) {
                    getLogger().log(Level.INFO, "Stopping BunJetty");
                } catch (Exception e) {
                    getLogger().log(Level.INFO, "Error starting BunJetty", e);
                }

            }
        });
    }

    private void loadConfig() throws Exception {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            file.createNewFile();
            try {
                InputStream in = getResourceAsStream("example_config.yml");
                OutputStream out = new FileOutputStream(file);
                ByteStreams.copy(in, out);
            } catch (Exception e) {
                getLogger().log(Level.INFO, "Error loading configuration", e);
            }
        }

        config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
    }

    public void initContextHandler() {
        for (HttpServlet servlet : servlets.keySet()) {
            contextHandler.addServlet(new ServletHolder(servlet), servlets.get(servlet));
            getLogger().log(Level.INFO, "Loaded servlet: " + servlet.getClass().getName());
        }
    }

    public void registerServlet(Plugin plugin, HttpServlet servlet) {
        String servletName = plugin.getDescription().getName() + "." + servlet.getClass().getSimpleName();
        if (servlets.containsKey(servlet)) {
            getLogger().log(Level.INFO, "Servlet already loaded: " + servletName);
            return;
        }
        String basePath = getConfig().getString("servlets." + servletName, null);
        getLogger().log(Level.INFO, "servlets." + servletName + " -> " + basePath);
        if (basePath == null) {
            getLogger().log(Level.INFO, "Invalid configuration for servlet " + servletName);
            return;
        }
        String joinedPath = new File(basePath, "/*").getPath();
        if (contextHandler != null) {
            contextHandler.addServlet(new ServletHolder(servlet), joinedPath);
        }
        servlets.put(servlet, joinedPath);
    }
}
