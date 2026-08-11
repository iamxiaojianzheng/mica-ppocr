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

package net.dreamlu.mica.ai.ppocr.structured.parser.idcard;

/**
 * 身份证版面枚举。
 *
 * <p>FRONT：人像面（姓名/性别/民族/出生日期/住址/公民身份号码）。
 * <p>BACK：国徽面（签发机关/有效期限）。
 * <p>UNKNOWN：无法判定（OCR 框异常或全为空）。
 */
public enum IdCardSide {
	/**
	 * 正面（人像面）
	 */
	FRONT,
	/**
	 * 反面（国徽面）
	 */
	BACK,
	/**
	 * 无法判定
	 */
	UNKNOWN
}
