package com.dayani.m.roboplatform.utils;

import android.content.Intent;
import android.os.Parcelable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;

public class AppUtils {

    ArrayList<Requirement> bili = new ArrayList<Requirement>();

    public static ArrayList<Requirement> sParcelables2Requirements(Parcelable[] reqs) {
        if (reqs == null) return null;
        ArrayList<Requirement> requirements = new ArrayList<>();
        for (Parcelable req : reqs) {
            requirements.add((Requirement) req);
        }
        return requirements;
    }

    public static Parcelable[] sRequirements2Parcelables(ArrayList<Requirement> reqs) {
        if (reqs == null) return null;
        Parcelable[] parcs = new Parcelable[reqs.size()];
        for (int i = 0; i < reqs.size(); i++) {
            parcs[i] = (Parcelable) reqs.get(i);
        }
        return parcs;
    }

    public static ArrayList<Requirement> removeRequirement(
            ArrayList<Requirement> requirements, Requirement req) {
        if (requirements == null || req == null) return requirements;
        requirements.remove(req);
        return requirements;
    }

    public static Class getClassFromIntent(Intent intent) {
        String targetName = intent.getStringExtra(AppGlobals.KEY_TARGET_ACTIVITY);
        Class tagetActivity = null;
        try {
            tagetActivity = Class.forName(targetName);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return tagetActivity;
    }

    public static byte[] serial(Serializable s) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(s);
            out.flush();
            return bos.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T unserial(byte[] b, Class<T> cl) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(b)) {
            ObjectInput in = null;
            in = new ObjectInputStream(bis);
            return (T) in.readObject();
        }
    }
}
