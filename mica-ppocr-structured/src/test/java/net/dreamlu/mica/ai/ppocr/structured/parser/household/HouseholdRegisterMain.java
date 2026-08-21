/*
 * Copyright (c) 2019-2026, dreamlu.net All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dreamlu.mica.ai.ppocr.structured.parser.household;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

/**
 * 户口本（常住人口登记卡）结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的户口本图片，运行 main 即可输出 OCR 框
 * + 结构化字段。
 */
public class HouseholdRegisterMain extends BaseTest<HouseholdRegisterParser, HouseholdRegisterResult> {

	private static final String IMAGE_PATH = "test_images/household_register/household_register1.png";
	private static final String VIS_PATH = "test_images/household_register/vis1.png";

	public static void main(String[] args) {
		new HouseholdRegisterMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected HouseholdRegisterParser newParser(PPOcrV6Engine engine) {
		return new HouseholdRegisterParser(engine);
	}

	@Override
	protected void printResult(HouseholdRegisterResult r) {
		System.out.println("== 户口本结构化结果 ==");
		System.out.println("户号:                       " + r.getHouseholdNo());
		System.out.println("姓名:                       " + r.getName());
		System.out.println("与户主关系:                 " + r.getRelationship());
		System.out.println("性别:                       " + r.getGender());
		System.out.println("出生地:                     " + r.getBirthPlace());
		System.out.println("民族:                       " + r.getEthnicity());
		System.out.println("籍贯:                       " + r.getNativePlace());
		System.out.println("出生日期:                   " + r.getBirthDate());
		System.out.println("公民身份号码:               " + r.getIdNumber());
		System.out.println("身高:                       " + r.getHeight());
		System.out.println("文化程度:                   " + r.getEducation());
		System.out.println("服务处所:                   " + r.getWorkplace());
		System.out.println("何时由何地迁来本市(县):     " + r.getMoveToCityDate());
		System.out.println("何时由何地迁往本址:         " + r.getMoveToAddress());
		System.out.println("登记日期:                   " + r.getRegistrationDate());
	}
}