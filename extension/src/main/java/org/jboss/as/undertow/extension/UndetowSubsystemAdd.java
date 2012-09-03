package org.jboss.as.undertow.extension;

import java.util.List;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.jbossallxml.JBossAllXmlParserRegisteringProcessor;
import org.jboss.as.web.WebExtension;
import org.jboss.as.undertow.deployment.ELExpressionFactoryProcessor;
import org.jboss.as.undertow.deployment.EarContextRootProcessor;
import org.jboss.as.undertow.deployment.JBossWebParsingDeploymentProcessor;
import org.jboss.as.undertow.deployment.ServletContainerInitializerDeploymentProcessor;
import org.jboss.as.undertow.deployment.TldParsingDeploymentProcessor;
import org.jboss.as.undertow.deployment.WarAnnotationDeploymentProcessor;
import org.jboss.as.undertow.deployment.WarClassloadingDependencyProcessor;
import org.jboss.as.undertow.deployment.WarDeploymentInitializingProcessor;
import org.jboss.as.undertow.deployment.WarDeploymentProcessor;
import org.jboss.as.undertow.deployment.WarMetaDataProcessor;
import org.jboss.as.undertow.deployment.WarStructureDeploymentProcessor;
import org.jboss.as.undertow.deployment.WebFragmentParsingDeploymentProcessor;
import org.jboss.as.undertow.deployment.WebJBossAllParser;
import org.jboss.as.undertow.deployment.WebParsingDeploymentProcessor;
import org.jboss.as.web.deployment.component.WebComponentProcessor;
import org.jboss.dmr.ModelNode;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.xnio.XnioWorker;


/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
class UndetowSubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final UndetowSubsystemAdd INSTANCE = new UndetowSubsystemAdd();

    private UndetowSubsystemAdd() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        for (SimpleAttributeDefinition attr : UndertowRootDefinition.ATTRIBUTES) {
            attr.validateAndSet(operation, model);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performBoottime(OperationContext context, ModelNode operation, final ModelNode model,
                                ServiceVerificationHandler verificationHandler, List<ServiceController<?>> newControllers)
            throws OperationFailedException {
        int writeThreads = UndertowRootDefinition.WRITE_THREADS.resolveModelAttribute(context, model).asInt();
        int readThreads = UndertowRootDefinition.READ_THREADS.resolveModelAttribute(context, model).asInt();
        int workerThreads = UndertowRootDefinition.WORKER_THREADS.resolveModelAttribute(context, model).asInt();

        final ServiceTarget target = context.getServiceTarget();
        final WorkerService workerService = new WorkerService(writeThreads, readThreads, workerThreads);
        newControllers.add(target.addService(WebSubsystemServices.XNIO_WORKER, workerService)
                .setInitialMode(ServiceController.Mode.ON_DEMAND)
                .install());


        context.addStep(new AbstractDeploymentChainStep() {
            @Override
            protected void execute(DeploymentProcessorTarget processorTarget) {

                final SharedWebMetaDataBuilder sharedWebBuilder = new SharedWebMetaDataBuilder(model.clone());
                final SharedTldsMetaDataBuilder sharedTldsBuilder = new SharedTldsMetaDataBuilder(model.clone());

                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_REGISTER_JBOSS_ALL_XML_PARSER, new JBossAllXmlParserRegisteringProcessor<JBossWebMetaData>(WebJBossAllParser.ROOT_ELEMENT, WebJBossAllParser.ATTACHMENT_KEY, new WebJBossAllParser()));
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_WAR_DEPLOYMENT_INIT, new WarDeploymentInitializingProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_WAR, new WarStructureDeploymentProcessor(sharedWebBuilder.create(), sharedTldsBuilder));
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_WEB_DEPLOYMENT, new WebParsingDeploymentProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_WEB_DEPLOYMENT_FRAGMENT, new WebFragmentParsingDeploymentProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_JBOSS_WEB_DEPLOYMENT, new JBossWebParsingDeploymentProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_TLD_DEPLOYMENT, new TldParsingDeploymentProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_ANNOTATION_WAR, new WarAnnotationDeploymentProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_WEB_COMPONENTS, new WebComponentProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_EAR_CONTEXT_ROOT, new EarContextRootProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_WEB_MERGE_METADATA, new WarMetaDataProcessor());

                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_WAR_MODULE, new WarClassloadingDependencyProcessor());

                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, Phase.POST_MODULE_EL_EXPRESSION_FACTORY, new ELExpressionFactoryProcessor());

                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_SERVLET_INIT_DEPLOYMENT, new ServletContainerInitializerDeploymentProcessor());
                processorTarget.addDeploymentProcessor(WebExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_WAR_DEPLOYMENT, new WarDeploymentProcessor("default"));
            }
        }, OperationContext.Stage.RUNTIME);


    }
}