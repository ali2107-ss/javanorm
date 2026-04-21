package ru.normacontrol.domain.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import ru.normacontrol.domain.entity.Violation;

import java.util.List;

/**
 * Интерфейс правила проверки ГОСТ 19.201-78 (Chain of Responsibility).
 * <p>
 * Каждая реализация отвечает за одну группу проверок
 * (структура, форматирование, таблицы, рисунки, язык).
 * Движок {@link GostRuleEngine} собирает все правила и вызывает
 * их последовательно, агрегируя нарушения.
 * </p>
 *
 * @see GostRuleEngine
 * @see ru.normacontrol.domain.entity.Violation
 */
public interface CheckRule {

    /**
     * Выполнить проверку документа по данной группе правил.
     *
     * @param document Apache POI XWPF-документ для анализа
     * @return список обнаруженных нарушений (пустой список, если нарушений нет)
     */
    List<Violation> check(XWPFDocument document);

    /**
     * Получить имя группы правил (для логирования и отчётности).
     *
     * @return человекочитаемое название группы проверок
     */
    String getRuleName();

    /**
     * Порядок выполнения правила в цепочке. Меньшее значение = раньше.
     *
     * @return числовой приоритет (по умолчанию 100)
     */
    default int getOrder() {
        return 100;
    }
}
