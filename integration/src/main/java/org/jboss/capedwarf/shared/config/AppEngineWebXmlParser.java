/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.capedwarf.shared.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.jboss.capedwarf.shared.xml.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Parses the appengine-web.xml file
 *
 * @author <a href="mailto:marko.luksa@gmail.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class AppEngineWebXmlParser {
    private static final Logger log = Logger.getLogger(AppEngineWebXmlParser.class.getName());

    public static AppEngineWebXml parse(InputStream inputStream) throws IOException {
        try {
            return tryParse(inputStream);
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private static AppEngineWebXml tryParse(InputStream inputStream) throws ParserConfigurationException, SAXException, IOException {
        Document doc = XmlUtils.parseXml(inputStream);
        Element documentElement = doc.getDocumentElement();

        Element systemPropertiesElement = XmlUtils.getChildElement(documentElement, "system-properties", false);
        if (systemPropertiesElement != null) {
            List<Element> propertyElements = XmlUtils.getChildren(systemPropertiesElement, "property");
            for (Element propertyElement : propertyElements) {
                String name = XmlUtils.getAttribute(propertyElement, "name");
                String value = XmlUtils.getAttribute(propertyElement, "value");
                System.setProperty(name, value);
            }
        }

        AppEngineWebXml appEngineWebXml = new AppEngineWebXml();

        appEngineWebXml.setApplication(XmlUtils.getChildElementBody(documentElement, "application"));
        appEngineWebXml.setVersion(XmlUtils.getChildElementBody(documentElement, "version"));
        appEngineWebXml.setThreadsafe(Boolean.parseBoolean(XmlUtils.getChildElementBody(documentElement, "threadsafe", false)));
        appEngineWebXml.setModule(XmlUtils.getChildElementBody(documentElement, "module", false));
        appEngineWebXml.setInstanceClass(XmlUtils.getChildElementBody(documentElement, "instance-class", false));
        appEngineWebXml.setPublicRoot(XmlUtils.getChildElementBody(documentElement, "public-root", false));

        Element inboundServices = XmlUtils.getChildElement(documentElement, "inbound-services");
        if (inboundServices != null) {
            InboundServices is = new InboundServices();
            List<Element> services = XmlUtils.getChildren(inboundServices, "service");
            for (Element service : services) {
                is.addService(XmlUtils.getBody(service));
            }
            appEngineWebXml.setInboundServices(is);
        }

        final Set<Scaling> scalings = new HashSet<>();
        Element manualScaling = XmlUtils.getChildElement(documentElement, "manual-scaling");
        if (manualScaling != null) {
            ManualScaling scaling = new ManualScaling();
            scaling.setInstances(Integer.parseInt(XmlUtils.getChildElementBody(manualScaling, "instances")));
            appEngineWebXml.setScaling(scaling);
            scalings.add(scaling);
        }
        Element basicScaling = XmlUtils.getChildElement(documentElement, "basic-scaling");
        if (basicScaling != null) {
            BasicScaling scaling = new BasicScaling();
            scaling.setMaxInstances(Integer.parseInt(XmlUtils.getChildElementBody(basicScaling, "max-instances")));
            scaling.setIdleTimeout(XmlUtils.getChildElementBody(basicScaling, "idle-timeout"));
            appEngineWebXml.setScaling(scaling);
            scalings.add(scaling);
        }
        Element autoScaling = XmlUtils.getChildElement(documentElement, "automatic-scaling");
        if (autoScaling != null) {
            AutomaticScaling scaling = new AutomaticScaling();
            scaling.setMinIdleInstances(XmlUtils.getChildElementBody(autoScaling, "min-idle-instances"));
            scaling.setMaxIdleInstances(XmlUtils.getChildElementBody(autoScaling, "max-idle-instances"));
            scaling.setMinPendingLatency(XmlUtils.getChildElementBody(autoScaling, "min-pending-latency"));
            scaling.setMaxPendingLatency(XmlUtils.getChildElementBody(autoScaling, "max-pending-latency"));
            appEngineWebXml.setScaling(scaling);
            scalings.add(scaling);
        }
        if (scalings.size() > 1) {
            throw new IllegalArgumentException("Multiple scaling types configured: " + scalings);
        }

        Element staticFilesElement = XmlUtils.getChildElement(documentElement, "static-files", false);
        if (staticFilesElement != null) {
            for (Element includeElement : XmlUtils.getChildren(staticFilesElement, "include")) {
                StaticFileInclude staticFileInclude = new StaticFileInclude(XmlUtils.getAttribute(includeElement, "path"), XmlUtils.getAttribute(includeElement, "expiration"));
                for (Element headerElement : XmlUtils.getChildren(includeElement, "http-header")) {
                    staticFileInclude.addHeader(new StaticFileHttpHeader(XmlUtils.getAttribute(headerElement, "name"), XmlUtils.getAttribute(headerElement, "value")));
                }
                appEngineWebXml.addStaticFileInclude(staticFileInclude);
            }

            for (Element excludeElement : XmlUtils.getChildren(staticFilesElement, "exclude")) {
                FilePattern exclude = new FilePattern(XmlUtils.getAttribute(excludeElement, "path"));
                appEngineWebXml.addStaticFileExclude(exclude);
            }
        }

        Element adminConsoleElement = XmlUtils.getChildElement(documentElement, "admin-console");
        if (adminConsoleElement != null) {
            for (Element pageElement : XmlUtils.getChildren(adminConsoleElement, "page")) {
                appEngineWebXml.addAdminConsolePage(XmlUtils.getAttribute(pageElement, "name"), XmlUtils.getAttribute(pageElement, "url"));
            }
        }

        Element sessionsEnabled = XmlUtils.getChildElement(documentElement, "sessions-enabled", false);
        if (sessionsEnabled != null) {
            String sev = XmlUtils.getBody(sessionsEnabled);
            if ("true".equalsIgnoreCase(sev)) {
                appEngineWebXml.setSessionType(SessionType.APPENGINE);
                Element asyncSessionPersistence = XmlUtils.getChildElement(documentElement, "async-session-persistence", false);
                if (asyncSessionPersistence != null) {
                    boolean enabledASP = Boolean.parseBoolean(XmlUtils.getAttribute(asyncSessionPersistence, "enabled"));
                    appEngineWebXml.setAsyncSessionPersistence(enabledASP);
                    if (enabledASP) {
                        String aspQueueName = XmlUtils.getAttribute(asyncSessionPersistence, "queue-name");
                        if (aspQueueName != null && aspQueueName.length() > 0) {
                            appEngineWebXml.setSessionPersistenceQueueName(aspQueueName);
                        }
                    }
                }
            } else if ("false".equalsIgnoreCase(sev)) {
                appEngineWebXml.setSessionType(SessionType.STUB);
            } else {
                log.info("<sessions-enabled> value equals " + sev);
                appEngineWebXml.setSessionType(SessionType.WILDFLY);
            }
        }

        Element warmupElt = XmlUtils.getChildElement(documentElement, "warmup-requests-enabled");
        if (warmupElt != null) {
            appEngineWebXml.setWarmupRequests(Boolean.parseBoolean(XmlUtils.getBody(warmupElt)));
        }

        return appEngineWebXml;
    }

}
