package com.oculifelis.perigee.client;

import com.mojang.math.Quadrant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oculifelis.perigee.Perigee;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.joml.Vector3f;

/**
 * Connected glass, named in a blockstate as {@code {"type": "perigee:connected", "textures": {...}}}, an optional {@code "tintindex"} with the five
 * sprites of {@link ConnectedGlass#VARIANTS}, and {@code "pattern": n} for glass whose picture spans n x n blocks. Every quadrant of every face is baked once per sprite; drawing a block
 * only picks from them.
 */
public final class ConnectedGlassModel {
    public static final Identifier ID = Perigee.id("connected");

    private ConnectedGlassModel() {
    }

    public static void register(RegisterBlockStateModels event) {
        event.registerModel(ID, Unbaked.CODEC);
    }

    public record Textures(Identifier isolated, Identifier all, Identifier h, Identifier v, Identifier inner,
                           Optional<Identifier> particle) {
        static final Codec<Textures> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("isolated").forGetter(Textures::isolated),
                Identifier.CODEC.fieldOf("all").forGetter(Textures::all),
                Identifier.CODEC.fieldOf("h").forGetter(Textures::h),
                Identifier.CODEC.fieldOf("v").forGetter(Textures::v),
                Identifier.CODEC.fieldOf("inner").forGetter(Textures::inner),
                Identifier.CODEC.optionalFieldOf("particle").forGetter(Textures::particle)).apply(i, Textures::new));

        Identifier[] list() {
            return new Identifier[] {isolated, all, h, v, inner};
        }
    }

    public record Unbaked(Textures textures, int pattern, int tint) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Textures.CODEC.fieldOf("textures").forGetter(Unbaked::textures),
                Codec.INT.optionalFieldOf("pattern", 1).forGetter(Unbaked::pattern),
                Codec.INT.optionalFieldOf("tintindex", -1).forGetter(Unbaked::tint)).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> codec() {
            return CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            // sprites only, no parent models
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            Identifier[] ids = textures.list();
            Material.Baked[] mats = new Material.Baked[ids.length];
            for (int i = 0; i < ids.length; i++) {
                mats[i] = baker.materials().get(new Material(ids[i], true), () -> ID.toString());
            }
            // quads[face][quadrant][sprite + 5 * tile]
            int n = Math.max(1, pattern), tiles = n * n;
            BakedQuad[][][] quads = new BakedQuad[6][4][mats.length * tiles];
            int flags = 0;
            for (Direction face : Direction.values()) {
                for (int sv = -1; sv <= 1; sv += 2) {
                    for (int su = -1; su <= 1; su += 2) {
                        float[] b = ConnectedGlass.box(face, su, sv);
                        Vector3f from = new Vector3f(b[0], b[1], b[2]);
                        Vector3f to = new Vector3f(b[3], b[4], b[5]);
                        for (int t = 0; t < tiles; t++) {
                            float[] uv = ConnectedGlass.uv(face, b, n, t);
                            CuboidFace cf = new CuboidFace(face, tint, "", new CuboidFace.UVs(uv[0], uv[1], uv[2], uv[3]), Quadrant.R0);
                            for (int s = 0; s < mats.length; s++) {
                                BakedQuad q = FaceBakery.bakeQuad(baker, from, to, cf, mats[s], face,
                                        BlockModelRotation.IDENTITY, null, true, 0);
                                quads[face.get3DDataValue()][ConnectedGlass.quadrant(su, sv)][s + mats.length * t] = q;
                                flags |= q.materialInfo().flags();
                            }
                        }
                    }
                }
            }
            Material.Baked particle = textures.particle()
                    .map(id -> baker.materials().get(new Material(id, true), () -> ID.toString()))
                    .orElse(mats[ConnectedGlass.ISOLATED]);
            return new Baked(quads, n, particle, flags);
        }
    }

    record Baked(BakedQuad[][][] quads, int n, Material.Baked particle, int flags) implements DynamicBlockStateModel {
        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            @SuppressWarnings("unchecked")
            List<BakedQuad>[] faces = new List[6];
            for (Direction face : Direction.values()) {
                List<BakedQuad> list = new ArrayList<>(4);
                int tile = ConnectedGlass.VARIANTS.length * ConnectedGlass.tile(pos, face, n);
                for (int sv = -1; sv <= 1; sv += 2) {
                    for (int su = -1; su <= 1; su += 2) {
                        int s = tile + ConnectedGlass.pick(level::getBlockState, pos, state, face, su, sv);
                        list.add(quads[face.get3DDataValue()][ConnectedGlass.quadrant(su, sv)][s]);
                    }
                }
                faces[face.get3DDataValue()] = list;
            }
            parts.add(new Part(faces, particle, flags));
        }

        /** Without a world (item frames, falling blocks): the plain look. */
        @Override
        public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
            @SuppressWarnings("unchecked")
            List<BakedQuad>[] faces = new List[6];
            for (Direction face : Direction.values()) {
                List<BakedQuad> list = new ArrayList<>(4);
                for (int q = 0; q < 4; q++) {
                    list.add(quads[face.get3DDataValue()][q][ConnectedGlass.ISOLATED]);
                }
                faces[face.get3DDataValue()] = list;
            }
            parts.add(new Part(faces, particle, flags));
        }

        @Override
        @SuppressWarnings("deprecation")
        public Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        @SuppressWarnings("deprecation")
        public int materialFlags() {
            return flags;
        }
    }

    record Part(List<BakedQuad>[] faces, Material.Baked particle, int flags) implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(Direction side) {
            return side == null ? List.of() : faces[side.get3DDataValue()];
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        public int materialFlags() {
            return flags;
        }
    }
}
