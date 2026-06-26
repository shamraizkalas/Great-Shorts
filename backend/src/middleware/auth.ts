import { Request, Response, NextFunction } from 'express';
import { auth as adminAuth } from '../config/firebase';

export interface AuthenticatedRequest extends Request {
  user?: {
    uid: string;
    email?: string;
    phone?: string;
    role?: string;
    [key: string]: any;
  };
}

export const verifyAuthToken = async (
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
) => {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({
      success: false,
      error: {
        message: 'Unauthorized. Authorization header is missing or malformed.',
        code: 'AUTH_HEADER_MISSING'
      }
    });
  }

  const token = authHeader.split('Bearer ')[1];

  try {
    const decodedToken = await adminAuth.verifyIdToken(token);
    req.user = {
      uid: decodedToken.uid,
      email: decodedToken.email,
      phone: decodedToken.phone_number,
      role: decodedToken.role || 'user',
      ...decodedToken
    };
    next();
  } catch (error) {
    console.error('Error verifying Firebase token:', error);
    res.status(401).json({
      success: false,
      error: {
        message: 'Unauthorized. Invalid or expired token.',
        code: 'INVALID_TOKEN'
      }
    });
  }
};
