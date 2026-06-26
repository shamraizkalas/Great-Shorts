import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from './auth';

export const verifyAdmin = (
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
) => {
  if (!req.user || req.user.role !== 'admin') {
    return res.status(403).json({
      success: false,
      error: {
        message: 'Forbidden. Admin privileges are required to perform this action.',
        code: 'FORBIDDEN_ADMIN_ONLY'
      }
    });
  }
  next();
};
