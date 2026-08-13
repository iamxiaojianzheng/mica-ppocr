package net.dreamlu.mica.ai.ppocr.solon.integration;

import net.dreamlu.mica.ai.ppocr.solon.OpenCVNativeLoader;
import net.dreamlu.mica.ai.ppocr.solon.PPOCRAutoConfiguration;
import net.dreamlu.mica.ai.ppocr.solon.PPOCRProperties;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;

/**
 *
 * @author noear 2026/8/13 created
 *
 */
public class PpocrPlugin implements Plugin {
	@Override
	public void start(AppContext context) throws Throwable {
		context.beanMake(PPOCRProperties.class);
		context.beanMake(OpenCVNativeLoader.class);
		context.beanMake(PPOCRAutoConfiguration.class);
	}
}
