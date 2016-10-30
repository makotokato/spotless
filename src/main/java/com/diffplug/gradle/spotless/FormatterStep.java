/*
 * Copyright 2016 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.gradle.spotless;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Suppliers;
import com.diffplug.common.base.Throwing;

/**
 * An implementation of this class specifies a single step in a formatting process.
 *
 * The input is guaranteed to have unix-style newlines, and the output is required
 * to not introduce any windows-style newlines as well.
 */
public interface FormatterStep extends Serializable {
	/** The name of the step, for debugging purposes. */
	String getName();

	/**
	 * Returns a formatted version of the given content.
	 *
	 * @param raw
	 *            File's content, guaranteed to have unix-style newlines ('\n')
	 * @param file
	 *            the File which is being formatted
	 * @return The formatted content, guaranteed to only have unix-style newlines
	 * @throws Throwable when the formatter steps experiences a problem
	 */
	String format(String raw, File file) throws Throwable;

	/**
	 * Returns a new FormatterStep which will only apply its changes
	 * to files which pass the given filter.
	 */
	default FormatterStep filterByFile(Predicate<File> filter) {
		return new FilterByFileFormatterStep(this, filter);
	}

	/** A FormatterStep which doesn't depend on the input file. */
	class FileIndependent implements FormatterStep {
		private final String name;
		private final Throwing.Function<String, String> formatter;

		private FileIndependent(String name, Throwing.Function<String, String> formatter) {
			this.name = name;
			this.formatter = formatter;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String format(String raw, File file) throws Throwable {
			return formatter.apply(raw);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			FileIndependent that = (FileIndependent) o;
			return Objects.equals(name, that.name) &&
					Objects.equals(formatter, that.formatter);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, formatter);
		}

		private static final long serialVersionUID = 1L;
	}

	/** Creates a FormatterStep from the given function. */
	static FormatterStep create(String name, Throwing.Function<String, String> formatter) {
		return new FileIndependent(name, formatter);
	}

	/** Creates a FormatterStep lazily from the given formatterSupplier function. */
	static FormatterStep createLazy(String name, Throwing.Supplier<Throwing.Function<String, String>> formatterSupplier) {
		// wrap the supplier as a regular Supplier (not a Throwing.Supplier)
		Supplier<Throwing.Function<String, String>> rethrowFormatterSupplier = Errors.rethrow().wrap(formatterSupplier);
		// memoize its result
		Supplier<Throwing.Function<String, String>> memoized = Suppliers.memoize(rethrowFormatterSupplier);
		// create the step
		return new FileIndependent(name, content -> memoized.get().apply(content));
	}
}
