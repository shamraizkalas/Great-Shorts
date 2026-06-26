import rateLimit from 'express-rate-limit';

// Global API Limiter
export const globalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // Limit each IP to 100 requests per window
  message: {
    success: false,
    error: {
      message: 'Too many requests from this IP. Please try again after 15 minutes.',
      code: 'RATE_LIMIT_EXCEEDED'
    }
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Watch tracking limiter: strict limit to prevent rapid fake API watch finishes
export const watchLimiter = rateLimit({
  windowMs: 1 * 60 * 1000, // 1 minute
  max: 10, // Limit watch start/finish events to 10 times a minute
  message: {
    success: false,
    error: {
      message: 'High frequency watch event detected. Watch claims are rate-limited to prevent abuse.',
      code: 'FRAUD_WATCH_RATE_LIMIT'
    }
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Withdrawal Request Limiter: strict limit
export const withdrawalLimiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 hour
  max: 3, // Max 3 withdrawal requests per hour per IP
  message: {
    success: false,
    error: {
      message: 'Withdrawal requests are rate-limited to avoid wallet spam.',
      code: 'WITHDRAWAL_RATE_LIMIT'
    }
  },
  standardHeaders: true,
  legacyHeaders: false,
});
