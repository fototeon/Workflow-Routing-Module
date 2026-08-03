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
