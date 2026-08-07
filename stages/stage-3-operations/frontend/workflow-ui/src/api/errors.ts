import axios from 'axios';
import type { ApiError } from './client';

/**
 * Turns a failed request into a message the operator can act on, separating "the backend never
 * answered" (the common local-setup problem) from an error the API itself reported.
 */
/** What the dev-server proxy and nginx answer when workflow-service itself is not reachable. */
const GATEWAY_STATUSES = [502, 503, 504];

export function describeLoadError(error: unknown, subject: string): string {
  if (axios.isAxiosError(error) && (!error.response || GATEWAY_STATUSES.includes(error.response.status))) {
    return `Не удалось загрузить ${subject}: сервис недоступен. Проверьте, что workflow-service запущен и отвечает на http://localhost:8090/actuator/health.`;
  }
  if (axios.isAxiosError<ApiError>(error) && error.response) {
    const reported = error.response.data?.message;
    return `Не удалось загрузить ${subject}: ${reported ?? `код ответа ${error.response.status}`}.`;
  }
  return `Не удалось загрузить ${subject}: ${error instanceof Error ? error.message : 'неизвестная ошибка'}.`;
}

/**
 * Same idea for an action the user just triggered. Axios only reports "Request failed with status
 * code 422", so the API's own message — and what that status means here — has to be unpacked.
 */
export function describeActionError(error: unknown, fallback: string): string {
  if (!axios.isAxiosError<ApiError>(error)) {
    return error instanceof Error ? error.message : fallback;
  }
  if (!error.response || GATEWAY_STATUSES.includes(error.response.status)) {
    return 'Сервис недоступен. Проверьте, что workflow-service запущен и отвечает на http://localhost:8090/actuator/health.';
  }

  const reported = error.response.data?.message;
  switch (error.response.status) {
    case 400:
      return reported ?? 'Проверьте заполнение полей формы.';
    case 403:
      return 'У вашей роли нет прав на это действие.';
    case 404:
      return 'Объект не найден — возможно, он уже удалён или изменён.';
    case 409:
      return reported
        ? `Действие недопустимо в текущем статусе: ${reported}`
        : 'Действие недопустимо в текущем статусе объекта.';
    case 422:
      return 'Ни одно правило маршрутизации шаблона не подошло к указанным атрибутам, поэтому следующий шаг не определён. '
        + 'Проверьте атрибуты или добавьте в шаблон правило по умолчанию.';
    default:
      return reported ?? fallback;
  }
}
