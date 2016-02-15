/**
 *
 * $Id$
 */
package library.validation;

import library.Book;
import library.Writer;

import org.eclipse.emf.common.util.EList;

/**
 * A sample validator interface for {@link library.Book}.
 * This doesn't really do anything, and it's not a real EMF artifact.
 * It was generated by the org.eclipse.emf.examples.generator.validator plug-in to illustrate how EMF's code generator can be extended.
 * This can be disabled with -vmargs -Dorg.eclipse.emf.examples.generator.validator=false.
 */
public interface BookValidator {
	boolean validate();

	boolean validateTitle(String value);
	boolean validateWriters(EList<Writer> value);
	boolean validateCitations(EList<Book> value);
}
