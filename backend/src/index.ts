import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import * as dotenv from 'dotenv';

// Middleware & Router imports
import apiRouter from './routes/api';
import { errorHandler } from './middleware/errorHandler';
import { globalLimiter } from './middleware/rateLimiter';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5000;

// Security & utilities middlewares
app.use(helmet()); // Secure HTTP headers
app.use(cors({ origin: '*' })); // Enable CORS
app.use(morgan('dev')); // Logger
app.use(express.json()); // JSON parser

// Apply global rate limiter
app.use('/api/', globalLimiter);

// Root Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'healthy',
    timestamp: new Date(),
    service: 'GreatShort Earning App API'
  });
});

// Primary API Router
app.use('/api', apiRouter);

// Global Error Handler Middleware
app.use(errorHandler);

app.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`🚀 GreatShort Production Backend running on port ${PORT}`);
  console.log(`====================================================`);
});

export default app;
