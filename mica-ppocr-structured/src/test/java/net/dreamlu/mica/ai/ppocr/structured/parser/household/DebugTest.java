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

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.ParserTestSupport;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调试用：打印所有 5 张图的关键字段。
 */
class DebugTest extends ParserTestSupport {

	private static final Pattern JSON_LINE = Pattern.compile(
		"\"text\":\"((?:[^\"\\\\]|\\\\.)*)\".*?\"box\":\\[" +
			"\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\]\\]");

	private static List<PPOcrV6Result> load(String name) throws IOException {
		String path = "/ocr-json/household_register/" + name + ".json";
		List<PPOcrV6Result> list = new ArrayList<>();
		try (InputStream is = DebugTest.class.getResourceAsStream(path);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = JSON_LINE.matcher(line);
				if (!m.find()) continue;
				String text = m.group(1)
					.replace("\\\"", "\"").replace("\\\\", "\\")
					.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
				int[][] box = {
					{Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))},
					{Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))},
					{Integer.parseInt(m.group(6)), Integer.parseInt(m.group(7))},
					{Integer.parseInt(m.group(8)), Integer.parseInt(m.group(9))}
				};
				list.add(new PPOcrV6Result(text, 1.0f, box));
			}
		}
		return list;
	}

	@Test
	void debug_print() throws IOException {
		for (int i = 1; i <= 5; i++) {
			String name = "household_register" + i;
			List<PPOcrV6Result> results = load(name);
			HouseholdRegisterResult r = new HouseholdRegisterParser(null).parseResults(results);
			System.out.println("\n[" + name + "]");
			System.out.println("  householdNo      = " + r.getHouseholdNo());
			System.out.println("  name             = " + r.getName());
			System.out.println("  relationship     = " + r.getRelationship());
			System.out.println("  gender           = " + r.getGender());
			System.out.println("  birthPlace       = " + r.getBirthPlace());
			System.out.println("  ethnicity        = " + r.getEthnicity());
			System.out.println("  nativePlace      = " + r.getNativePlace());
			System.out.println("  birthDate        = " + r.getBirthDate());
			System.out.println("  idNumber         = " + r.getIdNumber());
			System.out.println("  height           = " + r.getHeight());
			System.out.println("  education        = " + r.getEducation());
			System.out.println("  workplace        = " + r.getWorkplace());
			System.out.println("  moveToCityDate   = " + r.getMoveToCityDate());
			System.out.println("  moveToAddress    = " + r.getMoveToAddress());
			System.out.println("  registrationDate = " + r.getRegistrationDate());
		}
	}
}
