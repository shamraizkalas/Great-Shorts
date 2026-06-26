import { Response } from 'express';
import { AuthenticatedRequest } from '../middleware/auth';
import { collections } from '../config/firebase';
import { Video, Bookmark } from '../models/schemas';

export class VideoController {
  static async getVideos(req: AuthenticatedRequest, res: Response) {
    const { categoryId, search } = req.query;

    try {
      let query: FirebaseFirestore.Query = collections.videos.where('isSoftDeleted', '==', false);

      if (categoryId) {
        query = query.where('categoryId', '==', categoryId);
      }

      const snapshot = await query.get();
      let videos: Video[] = [];
      snapshot.forEach(doc => {
        videos.push({ id: doc.id, ...doc.data() } as Video);
      });

      // Simple keyword search filters in-memory (Firestore doesn't natively do text search without Algolia)
      if (search) {
        const term = String(search).toLowerCase();
        videos = videos.filter(
          v => v.title.toLowerCase().includes(term) || v.description.toLowerCase().includes(term)
        );
      }

      return res.status(200).json({
        success: true,
        data: videos
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async getVideoById(req: AuthenticatedRequest, res: Response) {
    const { id } = req.params;

    try {
      const doc = await collections.videos.doc(id).get();
      if (!doc.exists) {
        return res.status(404).json({ success: false, error: { message: 'Video not found' } });
      }

      // Increment views count safely
      doc.ref.update({ viewsCount: FirebaseFirestore.FieldValue.increment(1) });

      return res.status(200).json({
        success: true,
        data: { id: doc.id, ...doc.data() }
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async addBookmark(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    const { videoId } = req.body;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const bookmarkRef = collections.bookmarks.doc();
      const bookmark: Bookmark = {
        id: bookmarkRef.id,
        userId: uid,
        videoId,
        timestamp: FirebaseFirestore.Timestamp.now()
      };

      await bookmarkRef.set(bookmark);

      return res.status(201).json({
        success: true,
        data: bookmark
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async removeBookmark(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    const { id } = req.params; // Video bookmark ID
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const doc = await collections.bookmarks.doc(id).get();
      if (!doc.exists) {
        return res.status(404).json({ success: false, error: { message: 'Bookmark not found' } });
      }

      const bookmark = doc.data() as Bookmark;
      if (bookmark.userId !== uid) {
        return res.status(403).json({ success: false, error: { message: 'Forbidden' } });
      }

      await doc.ref.delete();

      return res.status(200).json({
        success: true,
        message: 'Bookmark removed successfully'
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }
}
