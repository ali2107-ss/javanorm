package ru.normacontrol.domain.checker.strategy;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import ru.normacontrol.domain.entity.Violation;

import java.util.List;

/**
 * Strategy contract for a single ГОСТ validation group.
 */
public interface CheckStrategy {

    /**
     * Return unique strategy code.
     *
     * @return strategy code
     */
    String getCode();

    /**
     * Return whether the strategy is enabled in configuration.
     *
     * @return {@code true} when the strategy should run
     */
    boolean isEnabled();

    /**
     * Execute the validation strategy against a DOCX document.
     *
     * @param doc source document
     * @return violations found by the strategy
     */
    List<Violation> execute(XWPFDocument doc);

    /**
     * Return strategy display name for logging.
     *
     * @return display name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Return execution order.
     *
     * @return lower value means earlier execution
     */
    default int getOrder() {
        return 100;
    }
}
